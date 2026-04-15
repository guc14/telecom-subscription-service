"""
main.py — FastAPI entry point for the Telecom AI Advisor sidecar.

Fix (404 bug): Java AdvisorService calls POST /api/v1/advise
but the original main.py only defined POST /advise.
This file now registers BOTH routes pointing to the same handler,
so both the Java service and manual Swagger testing work correctly.

Route mapping:
  POST /advise        → original path (kept for backward compatibility)
  POST /api/v1/advise → Java AdvisorService calls this path
  GET  /health        → health check (Docker + Spring Boot actuator can probe this)
"""

import logging
from fastapi import FastAPI, HTTPException
from app.agent import TelecomAdvisorAgent
from app.models import AdvisorRequest, AdvisorResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Telecom AI Advisor Sidecar",
    version="2.0.0",   # bumped to reflect LangGraph upgrade
    description="LangGraph-powered AI advisor with tool-calling, memory, and observability.",
)

# ── Shared agent instance ──────────────────────────────────────────────────────
# TelecomAdvisorAgent is stateless (state lives in MemorySaver inside agent.py)
# so it's safe to create one instance and reuse it across all requests.
agent = TelecomAdvisorAgent()


@app.get("/health")
async def health():
    """Health check endpoint — called by Docker healthcheck and Spring Boot."""
    return {"status": "ok", "version": "2.0.0"}


async def _handle_advise(request: AdvisorRequest) -> AdvisorResponse:
    """
    Shared handler for both /advise and /api/v1/advise routes.
    Extracts session_id from the request (defaults to customer-{id} if not provided).
    """
    logger.info(
        "Advise request: customerId=%d name=%s question=%s",
        request.customerId, request.customerName, request.question[:50],
    )
    try:
        # session_id: used as LangGraph thread_id for MemorySaver multi-turn memory
        # In this version we derive it from customerId; a real system would pass a UUID
        session_id = f"customer-{request.customerId}"
        return await agent.run(request, session_id=session_id)
    except Exception as e:
        logger.error("Agent error: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/advise", response_model=AdvisorResponse)
async def advise(request: AdvisorRequest):
    """Original endpoint — kept for backward compatibility and Swagger testing."""
    return await _handle_advise(request)


@app.post("/api/v1/advise", response_model=AdvisorResponse)
async def advise_v1(request: AdvisorRequest):
    """
    Primary endpoint — matches what Java AdvisorService calls.
    Java code: advisorUrl + "/api/v1/advise"
    Both routes share identical logic via _handle_advise().
    """
    return await _handle_advise(request)
