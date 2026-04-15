"""
config.py — Application settings loaded from environment variables.

Week 2 addition: MySQL connection settings for ai_call_log observability table.
Uses pydantic-settings which automatically reads from .env file and environment variables.
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── OpenAI settings ───────────────────────────────────────────────────────
    # openai_api_key: your OpenAI API key (never hardcode — always from env)
    openai_api_key: str
    # openai_model: which model to call; gpt-4o-mini is cheap and fast for tool-calling
    openai_model: str = "gpt-4o-mini"

    # ── Java service connection ───────────────────────────────────────────────
    # java_service_url: base URL for the Spring Boot service; tools call back to it
    java_service_url: str = "http://localhost:8080"
    # http_timeout_seconds: max wait for each Java API call; prevents hanging
    http_timeout_seconds: float = 10.0

    # ── MySQL settings (Week 2 — Observability) ───────────────────────────────
    # mysql_host: hostname of MySQL container (in Docker: "mysql", locally: "localhost")
    mysql_host: str = "mysql"
    # mysql_port: MySQL default port
    mysql_port: int = 3306
    # mysql_user / mysql_password: credentials matching docker-compose.yml
    mysql_user: str = "telecom"
    mysql_password: str = "telecom123"
    # mysql_database: the database where ai_call_log table lives
    mysql_database: str = "telecomdb"

    class Config:
        # env_file: pydantic-settings reads these variables from .env file automatically
        env_file = ".env"
        # extra = "ignore": silently ignore env vars not declared above (avoids errors)
        extra = "ignore"


# Module-level singleton — imported by agent.py, main.py, etc.
settings = Settings()
