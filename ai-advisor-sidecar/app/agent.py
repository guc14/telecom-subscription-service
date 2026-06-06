"""
agent.py — LangGraph-based Telecom Plan Advisor Agent
======================================================
Graph topology (Week 1 + Week 2 + Upgrade 1 RAG combined):
    call_model → (conditional) → tools → call_model → ...
                               ↘ END

Week 1: StateGraph, ToolNode, MemorySaver
Week 2: _record_metrics() → MySQL ai_call_log
Upgrade 1: retrieve_plan_context tool → ChromaDB RAG pipeline
"""

import json
import time
import logging
from typing import Annotated, Optional

from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode
from langgraph.checkpoint.memory import MemorySaver

from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from langchain_core.tools import tool

from typing_extensions import TypedDict

from app.models import AdvisorRequest, AdvisorResponse, PlanInfo, SubscriptionInfo
from app.registry_client import RegistryClient, RegistryClientError
from app.config import settings

# ── Upgrade 1: RAG module ─────────────────────────────────────────────────────
# rag.retrieve(query): loads ChromaDB, embeds query, returns top-3 similar plans
from app import rag

# ── Database imports for observability (Week 2) ────────────────────────────────
import mysql.connector

import asyncio

logger = logging.getLogger(__name__)

# ── MemorySaver: module-level singleton ────────────────────────────────────────
# One MemorySaver shared across all requests.
# Each session_id gets its own isolated memory thread.
memory = MemorySaver()


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 1: Graph State
# ══════════════════════════════════════════════════════════════════════════════

class AgentState(TypedDict):
    """
    The state that flows through every node in the graph.

    messages:    list of LangChain message objects (HumanMessage, AIMessage, ToolMessage)
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

# A module-level RegistryClient instance shared across tool calls.
_registry = RegistryClient()


# ── Upgrade 1: RAG tool ───────────────────────────────────────────────────────

@tool
def retrieve_plan_context(query: str) -> str:
    """
    Search the telecom plan catalogue using semantic similarity.
    Call this FIRST with the customer question as the query.
    Returns the most relevant plan descriptions as context.
    Use this context to ground your recommendation — do not guess plan details.
    """
    # rag.retrieve(): loads ChromaDB, embeds the query, returns top-3 similar plans
    # The returned string is injected directly into the LLM context window
    try:
        context = rag.retrieve(query)
        logger.info("RAG retrieved %d chars of context for query: %s",
                    len(context), query[:50])
        return context
    except RuntimeError as e:
        # ChromaDB not seeded yet: return helpful error instead of crashing
        # Agent will fall back to get_all_plans() based on system prompt
        logger.error("RAG retrieval failed: %s", e)
        return f"Plan catalogue unavailable: {e}. Falling back to get_all_plans()."


# ── Existing tools (unchanged) ────────────────────────────────────────────────

@tool
def get_subscribed_plans(customer_id: int) -> str:
    """
    Get the list of service plans a customer is currently subscribed to.
    Call this to understand what plans the customer already has.
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
    Use this to find plans the customer does NOT have yet.
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


# retrieve_plan_context listed FIRST — LLM sees it as the preferred entry point
TOOLS = [retrieve_plan_context, get_subscribed_plans, get_all_plans, get_subscription_details]


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 3: Observability Helper (Week 2 — unchanged)
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
        logger.warning("Failed to record AI metrics: %s", e)


# ══════════════════════════════════════════════════════════════════════════════
# SECTION 4: Graph Node Functions
# ══════════════════════════════════════════════════════════════════════════════

def _build_model() -> ChatOpenAI:
    """
    Construct the ChatOpenAI model with all tools bound.
    bind_tools(): attaches tool schemas so the LLM can emit tool_calls.
    """
    llm = ChatOpenAI(
        model=settings.openai_model,
        api_key=settings.openai_api_key,
        max_tokens=800,
        temperature=0,
    )
    return llm.bind_tools(TOOLS)


def call_model(state: AgentState) -> dict:
    """
    Node: call_model
    Sends the current message history to the LLM and appends the response.
    Records token usage + latency to MySQL on every call.
    """
    model = _build_model()
    t_start = time.time()

    response: AIMessage = model.invoke(state["messages"])

    latency_ms = int((time.time() - t_start) * 1000)

    usage = getattr(response, "usage_metadata", {}) or {}
    input_tokens = usage.get("input_tokens", 0)
    output_tokens = usage.get("output_tokens", 0)

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
        "messages": [response],
        "steps": state["steps"] + 1,
    }


def should_continue(state: AgentState) -> str:
    """
    Conditional edge: decides what happens after call_model.
    Returns "tools" if the LLM made tool calls, END otherwise.
    MAX_STEPS = 5 caps OpenAI API costs.
    """
    MAX_STEPS = 5

    last_message = state["messages"][-1]

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
    Graph structure:
        [START] → call_model → (conditional) → tools → call_model → ...
                                             ↘ END
    """
    builder = StateGraph(AgentState)

    builder.add_node("call_model", call_model)
    builder.add_node("tools", ToolNode(TOOLS))

    builder.set_entry_point("call_model")

    builder.add_conditional_edges(
        "call_model",
        should_continue,
        {
            "tools": "tools",
            END: END,
        },
    )

    builder.add_edge("tools", "call_model")

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
    """

    async def run(self, request: AdvisorRequest, session_id: Optional[str] = None) -> AdvisorResponse:
        sid = session_id or f"customer-{request.customerId}"

        # Upgrade 1: updated system prompt — instructs agent to use RAG tool first
        system_prompt = (
            "You are a helpful telecom service advisor. "
            "To make recommendations, follow this order: "
            "1. Call retrieve_plan_context(query) with the customer question to get relevant plan context. "
            "2. Call get_subscribed_plans(customer_id) to see what plans they already have. "
            "3. Based on the retrieved context and current subscriptions, give a personalised recommendation. "
            "Always mention monthly pricing. "
            "End your response with a 'Recommended plans:' section listing plan names with dashes."
        )

        user_message = (
            f"Customer: {request.customerName} (ID: {request.customerId}, Age: {request.age})\n"
            f"Question: {request.question}"
        )

        initial_state: AgentState = {
            "messages": [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_message),
            ],
            "customer_id": request.customerId,
            "session_id": sid,
            "steps": 0,
        }

        config = {"configurable": {"thread_id": sid}}

        try:
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