package com.guc.telecom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

/**
 * AiDescriptionService — uses Spring AI ChatClient to auto-generate a concise
 * service plan description when none is provided at creation time.
 *
 * Why Spring AI instead of raw RestTemplate:
 *   The previous implementation manually constructed HTTP headers, serialized
 *   the request body as a Map, and cast the response through unchecked generics.
 *   Spring AI's ChatClient eliminates all of that boilerplate and provides:
 *     - Provider portability: swap OpenAI → Azure OpenAI → Ollama via config only
 *     - Built-in retry and error handling (configured in application.properties)
 *     - SimpleLoggerAdvisor: logs prompt + response token usage automatically
 *     - Type-safe fluent API: no unchecked casts, no raw Map parsing
 *
 * Design principle — Non-blocking fallback (unchanged from previous version):
 *   This is a non-critical enrichment. If the call fails for any reason
 *   (network error, timeout, quota exceeded, missing key), the method catches
 *   the exception, logs a warning, and returns null. The caller (ServicePlanService)
 *   treats null as "no description" and saves the plan without one.
 *   The core subscription flow is never blocked by an external AI service failure.
 */
@Service
public class AiDescriptionService {

    private static final Logger logger = LoggerFactory.getLogger(AiDescriptionService.class);

    // System prompt: defines the LLM's persona and output format.
    // Kept as a constant so it's easy to tune without touching business logic.
    private static final String SYSTEM_PROMPT =
            "You are a telecom product marketing assistant. " +
            "Write concise, professional service plan descriptions in 2-3 sentences. " +
            "Focus on key benefits: speed, coverage, and value for money.";

    private final ChatClient chatClient;

    /**
     * ChatClient is auto-configured by Spring AI when spring.ai.openai.api-key
     * is present in application.properties. The builder is injected by Spring.
     *
     * SimpleLoggerAdvisor logs the full prompt and response at DEBUG level —
     * useful for inspecting token usage and prompt quality during development.
     */
    public AiDescriptionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                // Logs prompt + response + token usage at DEBUG level
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * Generates a marketing description for a telecom service plan using
     * Spring AI's ChatClient (backed by OpenAI GPT-4o-mini by default).
     *
     * Provider can be switched to Azure OpenAI or Ollama via config:
     *   spring.ai.openai.api-key       → OpenAI (default)
     *   spring.ai.azure.openai.*       → Azure OpenAI (enterprise / data residency)
     *   spring.ai.ollama.*             → Ollama local model (zero cost, air-gapped)
     *
     * @param planName  the name of the plan (e.g. "5G Unlimited Plus")
     * @return          a 2-3 sentence description, or null if the call fails
     */
    public String generateDescription(String planName) {
        try {
            String description = chatClient.prompt()
                    .user("Write a product description for this telecom service plan: " + planName)
                    .call()
                    .content();

            logger.info("Generated AI description for plan '{}'", planName);
            return description != null ? description.trim() : null;

        } catch (Exception ex) {
            // Non-critical: log and return null — caller saves plan without description
            logger.warn("AI description generation failed for plan '{}': {}", planName, ex.getMessage());
            return null;
        }
    }
}
