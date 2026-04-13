"""
agent.py — Telecom Plan Advisor AI Agent
=========================================
Tool-calling agent that autonomously queries the Java telecom service
to recommend the best service plans for a customer.

Agent loop:
  1. Build system prompt + customer context
  2. POST to OpenAI with tool definitions
  3. If model requests tools → execute → append result → repeat
  4. On finish_reason=stop → parse recommended plans → return response

Available tools:
  - get_subscribed_plans(customer_id)   → plans customer already has
  - get_all_plans()                     → full plan catalogue
  - get_subscription_details(customer_id) → activation timestamps + status

MAX_STEPS = 5 caps the loop to prevent runaway API costs.

"""

import json
import logging
from openai import AsyncOpenAI
from app.models import AdvisorRequest, AdvisorResponse, PlanInfo, SubscriptionInfo
from app.registry_client import RegistryClient, RegistryClientError
from app.config import settings

logger = logging.getLogger(__name__)

MAX_STEPS = 5

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_subscribed_plans",
            "description": (
                "Retrieve the list of service plans a customer is currently subscribed to. "
                "Call this first to understand what the customer already has."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "customer_id": {
                        "type": "integer",
                        "description": "The unique ID of the customer.",
                    }
                },
                "required": ["customer_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_all_plans",
            "description": (
                "Retrieve all service plans available in the telecom catalogue. "
                "Use this to find plans the customer has NOT yet subscribed to "
                "so you can make relevant recommendations."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_subscription_details",
            "description": (
                "Retrieve detailed subscription records for a customer, including "
                "activation date and status (ACTIVE/SUSPENDED/CANCELLED). "
                "Use for richer context about how long the customer has been on each plan."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "customer_id": {
                        "type": "integer",
                        "description": "The unique ID of the customer.",
                    }
                },
                "required": ["customer_id"],
            },
        },
    },
]


class TelecomAdvisorAgent:
    """
    Orchestrates the tool-calling loop between OpenAI and the Java telecom service.
    Instantiated per-request — stateless, no shared mutable state.
    """

    def __init__(self, registry_client: RegistryClient):
        self.client = AsyncOpenAI(api_key=settings.openai_api_key)
        self.registry = registry_client

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """
        Dispatch tool call to the appropriate RegistryClient method.
        Returns JSON string — OpenAI expects tool results as strings.
        Errors returned as JSON so the LLM can reason about failures gracefully.
        """
        logger.info("Executing tool: %s args=%s", tool_name, tool_args)

        try:
            if tool_name == "get_subscribed_plans":
                result: list[PlanInfo] = await self.registry.get_subscribed_plans(
                    tool_args["customer_id"]
                )
                return json.dumps([p.model_dump() for p in result])

            elif tool_name == "get_all_plans":
                result: list[PlanInfo] = await self.registry.get_all_plans()
                return json.dumps([p.model_dump() for p in result])

            elif tool_name == "get_subscription_details":
                result: list[SubscriptionInfo] = await self.registry.get_subscription_details(
                    tool_args["customer_id"]
                )
                return json.dumps([
                    {**s.model_dump(), "activated_at": s.activated_at.isoformat()}
                    for s in result
                ])

            else:
                return json.dumps({"error": f"Unknown tool: {tool_name}"})

        except RegistryClientError as e:
            logger.warning("Tool %s failed: %s", tool_name, e)
            return json.dumps({"error": str(e)})

    async def run(self, request: AdvisorRequest) -> AdvisorResponse:
        """Main agent entry point — runs tool-calling loop until final answer."""

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
            f"Customer: {request.customer_name} (ID: {request.customer_id}, Age: {request.age})\n"
            f"Question: {request.question}"
        )

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ]

        steps = 0

        while steps < MAX_STEPS:
            steps += 1
            logger.info("Agent step %d/%d", steps, MAX_STEPS)

            response = await self.client.chat.completions.create(
                model=settings.openai_model,
                messages=messages,
                tools=TOOLS,
                tool_choice="auto",
                max_tokens=800,
            )

            choice = response.choices[0]
            message = choice.message
            messages.append(message.model_dump(exclude_none=True))

            if choice.finish_reason == "tool_calls" and message.tool_calls:
                for tool_call in message.tool_calls:
                    tool_result = await self._execute_tool(
                        tool_call.function.name,
                        json.loads(tool_call.function.arguments),
                    )
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": tool_result,
                    })
                continue

            if choice.finish_reason in ("stop", "length"):
                advice_text = message.content or "No recommendation generated."
                recommended = _parse_recommended_plans(advice_text)

                return AdvisorResponse(
                    customer_id=request.customer_id,
                    customer_name=request.customer_name,
                    advice=advice_text,
                    recommended_plans=recommended,
                    agent_steps=steps,
                )

            logger.warning("Unexpected finish_reason: %s", choice.finish_reason)
            break

        return AdvisorResponse(
            customer_id=request.customer_id,
            customer_name=request.customer_name,
            advice="Unable to generate recommendation at this time. Please try again.",
            recommended_plans=[],
            agent_steps=steps,
        )


def _parse_recommended_plans(advice_text: str) -> list[str]:
    """
    Extract plan names listed after 'Recommended plans:' in the LLM output.

    Example:
        Recommended plans:
        - 5G Unlimited Plus
        - Home Fibre 500M

    Returns: ["5G Unlimited Plus", "Home Fibre 500M"]
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
