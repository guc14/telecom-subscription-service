package com.guc.telecom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AiDescriptionService — calls OpenAI Chat Completions API to auto-generate
 * a concise service plan description when none is provided at creation time.
 *
 * Design principle — Non-blocking fallback:
 *   This is a non-critical enrichment. If the API call fails for any reason
 *   (network error, timeout, quota exceeded, missing key), the method catches
 *   the exception, logs a warning, and returns null. The caller (ServicePlanService)
 *   treats null as "no description" and saves the plan without one.
 *   The core subscription flow is never blocked by an external AI service failure.
 *
 * This is the same pattern used for any non-critical third-party integration:
 * welcome email after registration, SMS notification after order — the core
 * transaction commits first, enrichment is best-effort.
 */
@Service
public class AiDescriptionService {

    private static final Logger logger = LoggerFactory.getLogger(AiDescriptionService.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public AiDescriptionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Generates a marketing description for a telecom service plan using GPT.
     *
     * @param planName  the name of the plan (e.g. "5G Unlimited Plus")
     * @return          a 2-3 sentence description, or null if the call fails
     */
    public String generateDescription(String planName) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("OpenAI API key not configured — skipping AI description generation");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "max_tokens", 120,
                "messages", List.of(
                    Map.of("role", "system",
                           "content", "You are a telecom product marketing assistant. " +
                                      "Write concise, professional service plan descriptions in 2-3 sentences."),
                    Map.of("role", "user",
                           "content", "Write a product description for this telecom service plan: " + planName)
                )
            );

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                OPENAI_URL,
                new HttpEntity<>(body, headers),
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");
            return content != null ? content.trim() : null;

        } catch (Exception ex) {
            logger.warn("AI description generation failed for plan '{}': {}", planName, ex.getMessage());
            return null;
        }
    }
}
