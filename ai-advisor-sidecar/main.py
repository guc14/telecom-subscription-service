"""main.py — FastAPI entry point for the Telecom AI Advisor sidecar."""

import logging
from fastapi import FastAPI, HTTPException
from app.agent import TelecomAdvisorAgent
from app.models import AdvisorRequest, AdvisorResponse
from app.registry_client import RegistryClient

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Telecom AI Advisor Sidecar", version="1.0.0")


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/advise", response_model=AdvisorResponse)
async def advise(request: AdvisorRequest):
    registry = RegistryClient()
    agent = TelecomAdvisorAgent(registry_client=registry)
    try:
        return await agent.run(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
