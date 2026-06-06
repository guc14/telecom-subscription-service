"""
seed_vectors.py — One-time ChromaDB seeding script.

Run once after docker-compose up -d:
    docker exec -it ai-advisor python seed_vectors.py

What it does:
  1. Calls GET http://java-service:8080/plans to fetch all service plans
  2. Calls rag.build_vectorstore(plans) to embed them into ./chroma_db/

Re-run whenever you add new service plans to the catalogue.
"""

import httpx
import logging
import sys

from app.rag import build_vectorstore
from app.config import settings

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def main():
    url = f"{settings.java_service_url}/plans"
    logger.info("Fetching service plans from %s ...", url)

    # httpx.get: synchronous HTTP GET
    # timeout=15.0: wait up to 15 seconds for Java service to respond
    try:
        response = httpx.get(url, timeout=15.0)
        response.raise_for_status()
    except httpx.HTTPError as e:
        logger.error("Failed to fetch plans: %s", e)
        sys.exit(1)

    # response.json(): parse JSON body
    # ["data"]: your Java ApiResponse wrapper puts the payload here
    plans = response.json().get("data", [])

    if not plans:
        logger.error("No plans returned. Is the Java service running?")
        sys.exit(1)

    logger.info("Received %d plans. Starting embedding...", len(plans))

    # build_vectorstore: embeds all plans and writes to ./chroma_db/
    build_vectorstore(plans)

    logger.info("Seeding complete. ChromaDB is ready.")


if __name__ == "__main__":
    main()