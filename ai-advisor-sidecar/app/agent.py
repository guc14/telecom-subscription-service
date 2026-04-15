"""
agent.py — LangGraph-based Telecom Plan Advisor Agent
======================================================
Upgrades the original hand-written while-loop to a proper LangGraph StateGraph.

Graph topology (Week 1 + Week 2 combined):
    intake → tool_node → respond
               ↑___________↓  (loop back if more tools needed)

Week 1 additions vs original:
  - StateGraph with typed AgentState (replaces manual messages list)
  - ToolNode (LangGraph built-in, replaces manual _execute_tool dispatch)
  - MemorySaver checkpointer → multi-turn conversation memory per session

Week 2 additions:
  - _record_metrics() → writes token usage + latency to MySQL ai_call_log table
  - Every LLM call records: session_id, input_tokens, output_tokens, latency_ms

Language: Python 3.11+
Key libraries: langgraph, langchain-openai, langchain-core
"""

import json
import time
import logging
from typing import Annotated, Optional

# ── LangGraph imports ──────────────────────────────────────────────────────────
# langgraph: the graph orchestration library
# StateGraph: the main class for building a node-based agent graph
# END: a sentinel value meaning "stop the graph here"
# MemorySaver: an in-memory checkpointer that saves conversation state between turns
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages   # reducer: appends new msgs to state
from langgraph.prebuilt import ToolNode            # built-in node that executes tool calls
from langgraph.checkpoint.memory import MemorySaver

# ── LangChain imports ──────────────────────────────────────────────────────────
# ChatOpenAI: LangChain wrapper around OpenAI chat completions
# bind_tools: attaches tool schemas to the model so it can emit tool_calls
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from langchain_core.tools import tool

# ── typing_extensions ──────────────────────────────────────────────────────────
# TypedDict: defines the shape of the graph state (like a typed dict / dataclass)
from typing_extensions import TypedDict

from app.models import AdvisorRequest, AdvisorResponse, PlanInfo, SubscriptionInfo
from app.registry_client import RegistryClient, RegistryClientError
from app.config import settings

# ── Database imports for observability (Week 2) ────────────────────────────────
import mysql.connector

logger = logging.getLogger(__name__)

# ── MemorySaver: module-level singleton ────────────────────────────────────────
# Why module-level: one MemorySaver shared across all requests so that
# conversation history persists between API calls for the same session_id.
# Each session_id gets its own isolated memory thread.
memory = MemorySaver()


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 1: Graph State
# ══════════════════════════════════════════════════════════════════════════════

class AgentState(TypedDict):
    """
    The state that flows through every node in the graph.

    messages: list of LangChain message objects (HumanMessage, AIMessage, ToolMessage)
              Annotated[..., add_messages] means: when updating, APPEND don't replace.
    customer_id: carried through state so tool functions can use it
    session_id:  used as the MemorySaver thread key for multi-turn memory
    steps:       how many LLM calls have been made (safety counter)
    """
    messages: Annotated[list, add_messages]
    customer_id: int
    session_id: str
    steps: int


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 2: Tool Definitions
# ══════════════════════════════════════════════════════════════════════════════
# @tool decorator: converts a Python function into a LangChain tool
# The docstring becomes the tool's description sent to the LLM
# The function signature becomes the tool's parameter schema (auto-generated)

# NOTE: ToolNode requires synchronous tools when used with invoke().
# We call the async RegistryClient via asyncio.run() inside each tool.
# In production you would use an async-native graph; this is clean enough for portfolio.

import asyncio

# A module-level RegistryClient instance shared across tool calls.
# This avoids creating a new HTTP client for every tool call.
_registry = RegistryClient()


@tool
def get_subscribed_plans(customer_id: int) -> str:
    """
    Get the list of service plans a customer is currently subscribed to.
    Call this first to understand what plans the customer already has.
    Returns a JSON string of plan objects with id, name, description, monthlyFeeCents.
    """
    try:
        plans: list[PlanInfo] = asyncio.run(_registry.get_subscribed_plans(customer_id))
        return json.dumps([p.model_dump() for p in plans])
    except RegistryClientError as e:
        logger.warning("get_subscribed_plans failed: %s", e)
        return json.dumps({"error": str(e)})


@tool
def get_all_plans() -> str:
    """
    Get all available service plans in the telecom catalogue.
    Use this after get_subscribed_plans to find plans the customer does NOT have yet,
    so you can make relevant upgrade or add-on recommendations.
    Returns a JSON string of all plan objects.
    """
    try:
        plans: list[PlanInfo] = asyncio.run(_registry.get_all_plans())
        return json.dumps([p.model_dump() for p in plans])
    except RegistryClientError as e:
        logger.warning("get_all_plans failed: %s", e)
        return json.dumps({"error": str(e)})


@tool
def get_subscription_details(customer_id: int) -> str:
    """
    Get detailed subscription records for a customer including activation date and status.
    Status values: ACTIVE, SUSPENDED, CANCELLED.
    Use this for richer context about how long the customer has been on each plan.
    Returns a JSON string of subscription detail objects.
    """
    try:
        subs: list[SubscriptionInfo] = asyncio.run(
            _registry.get_subscription_details(customer_id)
        )
        return json.dumps([
            {**s.model_dump(), "activatedAt": s.activatedAt.isoformat()}
            for s in subs
        ])
    except RegistryClientError as e:
        logger.warning("get_subscription_details failed: %s", e)
        return json.dumps({"error": str(e)})


# All tools collected in a list — passed to both the model and ToolNode
TOOLS = [get_subscribed_plans, get_all_plans, get_subscription_details]


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 3: Observability Helper (Week 2)
# ══════════════════════════════════════════════════════════════════════════════

def _record_metrics(
    session_id: str,
    customer_id: int,
    input_tokens: int,
    output_tokens: int,
    latency_ms: int,
    model: str,
) -> None:
    """
    Write one row to the ai_call_log table in MySQL.

    Why MySQL and not just logs:
      - Logs are ephemeral; DB rows can be queried by the Java /admin/ai-metrics endpoint
      - Enables token cost tracking, per-customer usage analysis, and alerting
      - Mirrors production observability patterns (e.g. Datadog APM custom metrics)

    Fails silently: observability must never break the main recommendation flow.
    """
    try:
        conn = mysql.connector.connect(
            host=settings.mysql_host,
            port=settings.mysql_port,
            user=settings.mysql_user,
            password=settings.mysql_password,
            database=settings.mysql_database,
        )
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO ai_call_log
                (session_id, customer_id, input_tokens, output_tokens, latency_ms, model_name)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (session_id, customer_id, input_tokens, output_tokens, latency_ms, model),
        )
        conn.commit()
        cursor.close()
        conn.close()
        logger.debug(
            "Metrics recorded: session=%s tokens_in=%d tokens_out=%d latency=%dms",
            session_id, input_tokens, output_tokens, latency_ms,
        )
    except Exception as e:
        # Fail silently — observability must not crash the main flow
        logger.warning("Failed to record AI metrics: %s", e)


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 4: Graph Node Functions
# ══════════════════════════════════════════════════════════════════════════════

def _build_model() -> ChatOpenAI:
    """
    Construct the ChatOpenAI model with tools bound.

    bind_tools(): attaches tool schemas to the model request payload.
    When the LLM decides to call a tool, it returns an AIMessage with
    tool_calls=[...] instead of a plain text response.
    """
    llm = ChatOpenAI(
        model=settings.openai_model,
        api_key=settings.openai_api_key,
        max_tokens=800,
        temperature=0,   # 0 = deterministic, better for tool-calling agents
    )
    return llm.bind_tools(TOOLS)


def call_model(state: AgentState) -> dict:
    """
    Node: call_model
    ─────────────────
    Sends the current message history to the LLM and appends the response.

    What this node does:
      1. Reads state["messages"] (the full conversation so far)
      2. POSTs to OpenAI with tool schemas attached
      3. Gets back either: a plain text response, or an AIMessage with tool_calls
      4. Records token usage + latency to MySQL (Week 2 observability)
      5. Returns updated messages and incremented steps counter

    state["messages"]: list — full conversation history (LangGraph injects this)
    state["steps"]:    int  — safety counter to prevent infinite loops
    """
    model = _build_model()
    t_start = time.time()

    response: AIMessage = model.invoke(state["messages"])

    latency_ms = int((time.time() - t_start) * 1000)

    # Extract token usage from the response metadata
    # response.usage_metadata is a dict: {"input_tokens": N, "output_tokens": M, ...}
    usage = getattr(response, "usage_metadata", {}) or {}
    input_tokens = usage.get("input_tokens", 0)
    output_tokens = usage.get("output_tokens", 0)

    # Week 2: record every LLM call to MySQL
    _record_metrics(
        session_id=state["session_id"],
        customer_id=state["customer_id"],
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        latency_ms=latency_ms,
        model=settings.openai_model,
    )

    logger.info(
        "LLM call complete: steps=%d latency=%dms tokens_in=%d tokens_out=%d",
        state["steps"] + 1, latency_ms, input_tokens, output_tokens,
    )

    return {
        "messages": [response],          # add_messages reducer will append this
        "steps": state["steps"] + 1,
    }


def should_continue(state: AgentState) -> str:
    """
    Conditional edge function: decides what happens after call_model.

    LangGraph conditional edges: instead of hardwiring "A → B",
    you provide a function that returns a string key, and LangGraph
    routes to the node mapped to that key.

    Logic:
      - If last message has tool_calls AND we haven't hit MAX_STEPS → "tools"
      - Otherwise → END (LangGraph's built-in terminal node)

    Returns:
      "tools" → route to the ToolNode (execute tool calls)
      END     → route to end of graph (return final state)
    """
    MAX_STEPS = 5   # matches original agent behaviour; caps OpenAI API costs

    last_message = state["messages"][-1]

    # Check if LLM requested tool calls
    has_tool_calls = (
        hasattr(last_message, "tool_calls")
        and last_message.tool_calls
    )

    if has_tool_calls and state["steps"] < MAX_STEPS:
        return "tools"

    return END


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 5: Build the Graph
# ══════════════════════════════════════════════════════════════════════════════

def _build_graph():
    """
    Construct and compile the LangGraph StateGraph.

    Graph structure:
        [START] → call_model → (conditional) → tools → call_model → ...
                                             ↘ END

    StateGraph(AgentState): declares the state schema for this graph.
      Every node receives the full state and returns a partial update dict.

    add_node(name, fn): registers a node. fn receives state, returns dict.
    add_edge(a, b):     hardwires a → b (always).
    add_conditional_edges(src, fn, mapping): after src, call fn(state)
      and route to the node whose name matches the return value.

    compile(checkpointer=memory): enables MemorySaver.
      Each invocation with the same config["configurable"]["thread_id"]
      resumes from where that thread left off (multi-turn memory).
    """
    builder = StateGraph(AgentState)

    # Register nodes
    builder.add_node("call_model", call_model)
    builder.add_node("tools", ToolNode(TOOLS))   # ToolNode handles tool_calls automatically

    # Entry point: always start at call_model
    builder.set_entry_point("call_model")

    # After call_model: conditionally go to tools or end
    builder.add_conditional_edges(
        "call_model",
        should_continue,
        {
            "tools": "tools",   # if should_continue returns "tools" → go to tools node
            END: END,           # if should_continue returns END → terminate
        },
    )

    # After tools: always go back to call_model (loop)
    builder.add_edge("tools", "call_model")

    # Compile with MemorySaver: persists state between invocations per thread_id
    return builder.compile(checkpointer=memory)


# Module-level compiled graph — built once, reused for all requests
graph = _build_graph()


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 6: Public Interface
# ══════════════════════════════════════════════════════════════════════════════

class TelecomAdvisorAgent:
    """
    Public interface — called by main.py FastAPI route.
    Wraps the LangGraph graph invocation to match the original API contract.

    session_id: if not provided, defaults to "customer-{customer_id}"
                In a real system this would be a UUID from the frontend session.
    """

    async def run(self, request: AdvisorRequest, session_id: Optional[str] = None) -> AdvisorResponse:
        sid = session_id or f"customer-{request.customerId}"

        system_prompt = (
            "You are a helpful telecom service advisor. "
            "Your goal is to recommend the best service plans for a customer "
            "based on their current subscriptions and needs. "
            "Always call get_subscribed_plans first to see what the customer already has, "
            "then call get_all_plans to find available options they don't have yet. "
            "Be specific, concise, and mention monthly pricing where relevant. "
            "End your response with a 'Recommended plans:' section listing plan names."
        )

        user_message = (
            f"Customer: {request.customerName} (ID: {request.customerId}, Age: {request.age})\n"
            f"Question: {request.question}"
        )

        # Initial state for this invocation
        initial_state: AgentState = {
            "messages": [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_message),
            ],
            "customer_id": request.customerId,
            "session_id": sid,
            "steps": 0,
        }

        # config: LangGraph uses thread_id to look up saved memory for this session
        config = {"configurable": {"thread_id": sid}}

        try:
            # graph.invoke() runs the full graph synchronously
            # Returns the final state after the graph reaches END
            final_state: AgentState = graph.invoke(initial_state, config=config)
        except Exception as e:
            logger.error("LangGraph agent failed: %s", e, exc_info=True)
            return AdvisorResponse(
                customer_id=request.customerId,
                customer_name=request.customerName,
                advice="Unable to generate recommendation at this time. Please try again.",
                recommended_plans=[],
                agent_steps=0,
            )

        # Extract the last AIMessage (the final LLM response)
        last_message = final_state["messages"][-1]
        advice_text = getattr(last_message, "content", "") or "No recommendation generated."
        steps_taken = final_state.get("steps", 0)

        return AdvisorResponse(
            customer_id=request.customerId,
            customer_name=request.customerName,
            advice=advice_text,
            recommended_plans=_parse_recommended_plans(advice_text),
            agent_steps=steps_taken,
        )


def _parse_recommended_plans(advice_text: str) -> list[str]:
    """
    Extract plan names listed after 'Recommended plans:' in the LLM output.
    Unchanged from original — same contract.
    """
    lines = advice_text.splitlines()
    plans, capturing = [], False

    for line in lines:
        stripped = line.strip()
        if "recommended plans" in stripped.lower():
            capturing = True
            continue
        if capturing and stripped.startswith("-"):
            name = stripped.lstrip("-").strip()
            if name:
                plans.append(name)
        elif capturing and stripped and not stripped.startswith("-"):
            break

    return plans
