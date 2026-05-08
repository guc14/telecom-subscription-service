package com.guc.telecom.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AiMetricsController — exposes LLM call observability data stored by the Python sidecar.
 *
 * Week 2 addition: every time the LangGraph agent calls OpenAI, the Python sidecar
 * writes a row to ai_call_log (session_id, tokens, latency_ms, model_name).
 * This endpoint aggregates that data so it can be monitored without direct DB access.
 *
 * Endpoints:
 *   GET /admin/ai-metrics/summary    → total calls, total tokens, avg latency
 *   GET /admin/ai-metrics/recent     → last 20 individual LLM calls
 *   GET /admin/ai-metrics/by-customer/{id} → calls for a specific customer
 *
 * In a production system you would add authentication (Spring Security) to /admin/*.
 * For portfolio purposes the endpoint is open but clearly scoped to /admin.
 */
@RestController
@RequestMapping("/admin/ai-metrics")
@Tag(name = "AI Metrics API", description = "LLM call observability — token usage, latency, and call history")
public class AiMetricsController {

    // JdbcTemplate: Spring's lightweight JDBC wrapper
    // Why JdbcTemplate and not JPA: ai_call_log is a log/append-only table,
    // not a domain entity. Plain SQL is simpler and faster for this use case.
    private final JdbcTemplate jdbc;

    public AiMetricsController(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    /**
     * GET /admin/ai-metrics/summary
     * Returns aggregate statistics across all LLM calls.
     *
     * Example response:
     * {
     *   "total_calls": 42,
     *   "total_input_tokens": 18500,
     *   "total_output_tokens": 6200,
     *   "avg_latency_ms": 847,
     *   "unique_sessions": 15
     * }
     */
    @Operation(
        summary = "Get aggregate AI call statistics",
        description = "Returns total LLM calls, token counts, average latency, and unique session count."
    )
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        // queryForMap: executes the SQL and returns a single row as Map<column, value>
        // Throws EmptyResultDataAccessException if no rows exist — won't happen with COUNT/SUM
        return jdbc.queryForMap(
            """
            SELECT
                COUNT(*)                    AS total_calls,
                COALESCE(SUM(input_tokens), 0)  AS total_input_tokens,
                COALESCE(SUM(output_tokens), 0) AS total_output_tokens,
                COALESCE(AVG(latency_ms), 0)    AS avg_latency_ms,
                COUNT(DISTINCT session_id)  AS unique_sessions
            FROM ai_call_log
            """
        );
    }

    /**
     * GET /admin/ai-metrics/recent?limit=20
     * Returns the most recent individual LLM call records.
     *
     * @param limit  max number of rows to return (default 20, max 100)
     */
    @Operation(
        summary = "Get recent LLM call records",
        description = "Returns individual call records ordered by most recent first. Use for debugging slow calls."
    )
    @GetMapping("/recent")
    public List<Map<String, Object>> getRecent(
            @RequestParam(defaultValue = "20") int limit) {

        // Cap at 100 to prevent accidentally returning millions of rows
        int safeLimit = Math.min(limit, 100);

        // queryForList: returns each row as a Map<column, value>; all rows as a List
        return jdbc.queryForList(
            """
            SELECT id, session_id, customer_id, input_tokens, output_tokens,
                   latency_ms, model_name, created_at
            FROM ai_call_log
            ORDER BY created_at DESC
            LIMIT ?
            """,
            safeLimit
        );
    }

    /**
     * GET /admin/ai-metrics/by-customer/{customerId}
     * Returns all LLM calls made for a specific customer.
     * Useful for debugging "why did customer 101 get this recommendation".
     */
    @Operation(
        summary = "Get AI call history for a specific customer",
        description = "Returns all LLM calls for the given customer ID, ordered by most recent first."
    )
    @GetMapping("/by-customer/{customerId}")
    public List<Map<String, Object>> getByCustomer(@PathVariable Long customerId) {
        return jdbc.queryForList(
            """
            SELECT id, session_id, input_tokens, output_tokens,
                   latency_ms, model_name, created_at
            FROM ai_call_log
            WHERE customer_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """,
            customerId
        );
    }
}
