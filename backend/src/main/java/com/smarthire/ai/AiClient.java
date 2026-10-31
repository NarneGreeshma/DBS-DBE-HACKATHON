package com.smarthire.ai;

import com.smarthire.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client for the FastAPI resume-intelligence service.
 *
 * <p>It is deliberately thin: no retries, no caching, and every failure is allowed to
 * surface as an exception so callers can degrade gracefully. The browser never talks to
 * this service directly - only the primary Java backend does.
 */
@Slf4j
@Component
public class AiClient {

    private final RestClient restClient;
    private final boolean enabled;

    public AiClient(AppProperties properties) {
        this.enabled = properties.getAi().isEnabled();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMillis(properties.getAi().getTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getAi().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public AiDtos.AnalyzeResponse analyze(AiDtos.AnalyzeRequest request) {
        return restClient.post()
                .uri("/analyze")
                .body(request)
                .retrieve()
                .body(AiDtos.AnalyzeResponse.class);
    }

    public boolean isHealthy() {
        try {
            AiDtos.HealthResponse health = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(AiDtos.HealthResponse.class);
            return health != null && "UP".equalsIgnoreCase(health.status());
        } catch (RuntimeException ex) {
            log.debug("AI service health check failed: {}", ex.getMessage());
            return false;
        }
    }
}
