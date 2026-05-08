-- ============================================================
-- V2__create_ai_call_log.sql
-- Week 2 Observability: stores every LLM call made by the
-- Python sidecar agent so Java can expose metrics via REST.
--
-- Run this once against telecomdb:
--   docker exec -i <mysql-container> mysql -utelecom -ptelecom123 telecomdb < V2__create_ai_call_log.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_call_log (
    -- id: auto-increment primary key — uniquely identifies each LLM call
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- session_id: the LangGraph thread_id, format "customer-{customerId}"
    --             Used to group all calls in one conversation
    session_id      VARCHAR(255) NOT NULL,

    -- customer_id: which customer triggered this LLM call
    customer_id     BIGINT NOT NULL,

    -- input_tokens: number of tokens in the prompt sent to OpenAI
    --               Higher = more expensive; used for cost tracking
    input_tokens    INT NOT NULL DEFAULT 0,

    -- output_tokens: number of tokens in the model's response
    output_tokens   INT NOT NULL DEFAULT 0,

    -- latency_ms: wall-clock time for the OpenAI API call in milliseconds
    --             Used to detect slow responses and set SLA alerts
    latency_ms      INT NOT NULL DEFAULT 0,

    -- model_name: which OpenAI model was used (e.g. "gpt-4o-mini")
    --             Useful when comparing cost/quality across models
    model_name      VARCHAR(100) NOT NULL,

    -- created_at: timestamp of the call; auto-set by MySQL on INSERT
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Index on session_id: speeds up queries like "all calls for session X"
    INDEX idx_session_id  (session_id),

    -- Index on customer_id: speeds up queries like "all calls for customer 101"
    INDEX idx_customer_id (customer_id),

    -- Index on created_at: speeds up time-range queries for dashboards
    INDEX idx_created_at  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
