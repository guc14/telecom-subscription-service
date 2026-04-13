package com.guc.telecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate bean — used by AiDescriptionService for OpenAI calls
     * and by AdvisorService for Python sidecar calls.
     *
     * Declared here once so both services receive the same managed instance
     * rather than constructing their own (which bypasses Spring's proxy chain
     * and makes mocking in tests harder).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
