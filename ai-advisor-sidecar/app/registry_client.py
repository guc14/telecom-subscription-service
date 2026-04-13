"""
registry_client.py — HTTP client for the Java Telecom Subscription Service
===========================================================================
Single place for all outbound HTTP calls from the Python sidecar to Java.

Why a dedicated client module:
  - Single place for base URL, timeouts, error handling
  - Easy to mock in tests (patch RegistryClient methods)
  - Mirrors Repository pattern: callers don't know or care about HTTP details

Uses httpx (async) so FastAPI's event loop is never blocked waiting for Java.
"""

import httpx
import logging
from typing import Optional
from app.models import PlanInfo, SubscriptionInfo
from app.config import settings

logger = logging.getLogger(__name__)


class RegistryClientError(Exception):
    """Raised when the Java telecom service returns an error or is unreachable."""
    pass


class RegistryClient:
    """Async HTTP client wrapping the Java Telecom REST API."""

    def __init__(self, base_url: Optional[str] = None):
        self.base_url = (base_url or settings.java_service_url).rstrip("/")

    async def get_subscribed_plans(self, customer_id: int) -> list[PlanInfo]:
        """GET /plans/by-customer/{customer_id} — plans the customer is on."""
        url = f"{self.base_url}/plans/by-customer/{customer_id}"
        logger.info("Fetching subscribed plans for customer %d", customer_id)

        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            try:
                response = await client.get(url)
                response.raise_for_status()
            except httpx.HTTPStatusError as e:
                raise RegistryClientError(
                    f"Java service returned {e.response.status_code} for {url}"
                ) from e
            except httpx.RequestError as e:
                raise RegistryClientError(f"Cannot reach Java service at {url}: {e}") from e

        data = response.json().get("data", [])
        return [PlanInfo(**item) for item in data]

    async def get_all_plans(self) -> list[PlanInfo]:
        """GET /plans — full service plan catalogue."""
        url = f"{self.base_url}/plans"
        logger.info("Fetching all service plans")

        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            try:
                response = await client.get(url)
                response.raise_for_status()
            except httpx.HTTPStatusError as e:
                raise RegistryClientError(
                    f"Java service returned {e.response.status_code} for {url}"
                ) from e
            except httpx.RequestError as e:
                raise RegistryClientError(f"Cannot reach Java service at {url}: {e}") from e

        data = response.json().get("data", [])
        return [PlanInfo(**item) for item in data]

    async def get_subscription_details(self, customer_id: int) -> list[SubscriptionInfo]:
        """GET /plans/subscriptions/by-customer/{customer_id} — full subscription history."""
        url = f"{self.base_url}/plans/subscriptions/by-customer/{customer_id}"
        logger.info("Fetching subscription details for customer %d", customer_id)

        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            try:
                response = await client.get(url)
                response.raise_for_status()
            except httpx.HTTPStatusError as e:
                raise RegistryClientError(
                    f"Java service returned {e.response.status_code} for {url}"
                ) from e
            except httpx.RequestError as e:
                raise RegistryClientError(f"Cannot reach Java service at {url}: {e}") from e

        data = response.json().get("data", [])
        return [SubscriptionInfo(**item) for item in data]
