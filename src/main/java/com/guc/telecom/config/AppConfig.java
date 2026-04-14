package com.guc.telecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate bean — used by AdvisorService for HTTP calls to the
     * Python FastAPI sidecar (POST /api/v1/advise).
     *
     * Note: AiDescriptionService previously used RestTemplate to call the
     * OpenAI API directly. That has been replaced by Spring AI's ChatClient,
     * which is auto-configured by Spring AI and does not require a RestTemplate.
     * RestTemplate is retained here solely for the sidecar integration.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
