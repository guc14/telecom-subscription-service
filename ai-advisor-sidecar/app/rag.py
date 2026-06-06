"""
rag.py — RAG pipeline for the Telecom AI Advisor
=================================================
Responsibility 1: embed telecom service plan documents into ChromaDB.
Responsibility 2: expose retrieve(query) for the LangGraph agent tool.

Architecture:
  seed_vectors.py (one-time) → calls build_vectorstore() → writes ./chroma_db/
  agent.py (every request)   → calls retrieve(query)     → reads ./chroma_db/

Embedding model: text-embedding-3-small (1536 dimensions)
  - Cheapest OpenAI embedding model: $0.02 per 1M tokens
  - Sufficient for short telecom plan descriptions (< 200 tokens each)
"""

# os: used to check if chroma_db directory already exists
import os

# logging: standard Python logging
import logging

# List, Dict: Python type hints
from typing import List, Dict

# OpenAIEmbeddings: LangChain wrapper around OpenAI text-embedding-3-small
# Takes a string → returns a list of 1536 floats (the embedding vector)
from langchain_openai import OpenAIEmbeddings

# Chroma: LangChain wrapper around ChromaDB vector store
from langchain_chroma import Chroma

# Document: LangChain data class — wraps text + metadata
# page_content = the text that gets embedded
# metadata     = extra fields stored alongside (not embedded)
from langchain_core.documents import Document

from app.config import settings

logger = logging.getLogger(__name__)

# CHROMA_PATH: where ChromaDB persists its data on disk
# In Docker this maps to a named volume
CHROMA_PATH = "./chroma_db"

# COLLECTION_NAME: ChromaDB organises vectors into named collections
# Like a table name in a relational DB
COLLECTION_NAME = "telecom_plans"

# TOP_K: how many plan chunks to retrieve per query
# 3 is enough for plan recommendation; higher = more tokens = more cost
TOP_K = 3


def _get_embeddings() -> OpenAIEmbeddings:
    """
    Return an OpenAIEmbeddings instance using text-embedding-3-small.

    Why text-embedding-3-small:
      - 1536-dimension vectors
      - $0.02 per 1M tokens (cheapest OpenAI embedding model)
      - Sufficient for short telecom plan descriptions
    """
    return OpenAIEmbeddings(
        openai_api_key=settings.openai_api_key,
        model="text-embedding-3-small",
    )


def build_vectorstore(plans: List[Dict]) -> Chroma:
    """
    Embed plan documents and persist them to ChromaDB.
    Called ONCE by seed_vectors.py — not on every request.

    Args:
        plans: list of dicts with keys: id, name, description, monthlyFeeCents, capacity

    Returns:
        Chroma: the loaded vector store
    """
    docs = []
    for plan in plans:
        # Format monthly fee as dollars for natural language understanding
        fee_dollars = (plan.get("monthlyFeeCents") or 0) / 100

        # page_content: the TEXT that gets embedded into a vector
        # Structured as natural language so the embedding captures intent
        text = (
            f"Plan name: {plan['name']}. "
            f"Description: {plan.get('description') or 'No description'}. "
            f"Monthly fee: ${fee_dollars:.2f}. "
            f"Capacity: {plan.get('capacity') or 'Unlimited'}."
        )

        # metadata: stored in ChromaDB but NOT embedded
        # Retrieved alongside the text so the agent knows the plan ID
        metadata = {
            "plan_id":   str(plan["id"]),
            "plan_name": plan["name"],
            "fee_cents": str(plan.get("monthlyFeeCents") or 0),
        }

        docs.append(Document(page_content=text, metadata=metadata))

    logger.info("Embedding %d plan documents into ChromaDB...", len(docs))

    # Chroma.from_documents():
    #   - Calls OpenAI embeddings API for each Document
    #   - Stores (vector, metadata, text) tuples in ./chroma_db/
    #   - persist_directory: where to write the SQLite + vector files
    vectorstore = Chroma.from_documents(
        documents=docs,
        embedding=_get_embeddings(),
        persist_directory=CHROMA_PATH,
        collection_name=COLLECTION_NAME,
    )

    logger.info("ChromaDB vectorstore built at %s with %d docs", CHROMA_PATH, len(docs))
    return vectorstore


def retrieve(query: str) -> str:
    """
    Retrieve the TOP_K most relevant plan documents for a query.
    Called by the LangGraph agent tool on every recommendation request.

    Args:
        query: the customer question

    Returns:
        str: formatted string of retrieved plan chunks — passed to LLM as context

    Raises:
        RuntimeError: if ChromaDB has not been seeded
    """
    if not os.path.exists(CHROMA_PATH):
        raise RuntimeError(
            "ChromaDB not seeded. Run: docker exec -it ai-advisor python seed_vectors.py"
        )

    # Load existing ChromaDB collection from disk — does NOT re-embed
    vectorstore = Chroma(
        persist_directory=CHROMA_PATH,
        embedding_function=_get_embeddings(),
        collection_name=COLLECTION_NAME,
    )

    # similarity_search():
    #   1. Embeds the query string using the same model as seeding
    #   2. Finds the k nearest vectors by cosine similarity
    #   3. Returns list[Document] sorted by similarity (most similar first)
    results: List[Document] = vectorstore.similarity_search(query, k=TOP_K)

    if not results:
        return "No relevant plans found in the catalogue."

    # Format results as readable string for the LLM
    formatted = []
    for i, doc in enumerate(results, 1):
        formatted.append(
            f"[Plan {i}] {doc.metadata.get('plan_name', 'Unknown')}\n"
            f"{doc.page_content}\n"
            f"Plan ID: {doc.metadata.get('plan_id', '?')} | "
            f"Fee: ${int(doc.metadata.get('fee_cents', 0)) / 100:.2f}/month"
        )

    # Join with separator so the LLM can clearly distinguish plan chunks
    return "\n\n---\n\n".join(formatted)