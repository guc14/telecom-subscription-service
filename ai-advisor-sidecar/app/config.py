"""config.py — Application settings loaded from environment variables."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    openai_api_key: str
    openai_model: str = "gpt-4o-mini"
    java_service_url: str = "http://localhost:8080"
    http_timeout_seconds: float = 10.0

    class Config:
        env_file = ".env"


settings = Settings()
