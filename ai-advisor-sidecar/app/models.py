"""models.py — Pydantic schemas for the telecom AI advisor sidecar."""

from pydantic import BaseModel
from datetime import datetime
from typing import Optional


class PlanInfo(BaseModel):
    """Mirrors Java ServicePlanDto."""
    id: int
    name: str
    description: Optional[str] = None
    monthlyFeeCents: Optional[int] = None
    capacity: Optional[int] = None


class SubscriptionInfo(BaseModel):
    """Mirrors Java SubscriptionInfoDto."""
    subscriptionId: int
    customerId: int
    customerName: str
    planId: int
    planName: str
    activatedAt: datetime
    status: str


class AdvisorRequest(BaseModel):
    """Request body from Java AdvisorService."""
    customerId: int
    customerName: str
    age: int
    question: str


class AdvisorResponse(BaseModel):
    """Response returned to Java AdvisorService."""
    customer_id: int
    customer_name: str
    advice: str
    recommended_plans: list[str]
    agent_steps: int
