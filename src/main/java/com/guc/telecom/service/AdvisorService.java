package com.guc.telecom.service;

import com.guc.telecom.dto.AdvisorRequest;
import com.guc.telecom.dto.AdvisorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * AdvisorService — calls the Python AI sidecar to generate personalised
 * service plan recommendations for a customer.
 *
 * Architecture:
 *   Java (this service) → POST /api/v1/advise → Python FastAPI sidecar
 *   Python sidecar → OpenAI function-calling agent loop
 *   Agent tools → GET /plans, GET /plans/by-customer/{id} (back to Java)
 *
 * Fallback:
 *   If the Python sidecar is down or times out, ResourceAccessException is caught
 *   and null is returned. The controller maps null → HTTP 503.
 *   Core subscription CRUD is never affected by sidecar availability.
 */
@Service
public class AdvisorService {

    private static final Logger logger = LoggerFactory.getLogger(AdvisorService.class);

    @Value("${ai.advisor.url:http://localhost:8000}")
    private String advisorUrl;

    private final RestTemplate restTemplate;

    public AdvisorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AdvisorResponse getRecommendation(Long customerId, String customerName,
                                              Integer age, String question) {
        String url = advisorUrl + "/api/v1/advise";

        AdvisorRequest request = new AdvisorRequest(customerId, customerName, age, question);

        try {
            AdvisorResponse response = restTemplate.postForObject(url, request, AdvisorResponse.class);
            logger.info("Advisor response received for customerId={}, steps={}",
                customerId, response != null ? response.getAgentSteps() : "null");
            return response;
        } catch (ResourceAccessException ex) {
            logger.warn("AI advisor sidecar unavailable at {}: {}", url, ex.getMessage());
            return null;
        } catch (Exception ex) {
            logger.error("Unexpected error calling advisor sidecar: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
