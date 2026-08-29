package com.ratelimiter.rate_limiter_service.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private int defaultCapacity = 10;
    private double defaultRefillRate = 2.0;
    private boolean failOpen = true;

    // ponytail: ConcurrentHashMap for thread-safe binding, HashMap would also work as Spring binds single-threaded
    private Map<String, ClientConfig> clients = new ConcurrentHashMap<>();


    // Priority: endpoint-specific > client-level > global defaults

    public EndpointConfig resolveForClientAndEndpoint(String clientId, String endpoint) {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        ClientConfig clientConfig = clients.get(clientId);

        // 1. Endpoint-specific config (highest priority)
        if (clientConfig != null) {
            EndpointConfig epConfig = clientConfig.getEndpoints().get(normalizedEndpoint);
            if (epConfig != null && epConfig.getCapacity() > 0 && epConfig.getRefillRate() > 0) {
                return epConfig;
            }
        }

        // 2. Client-level defaults
        if (clientConfig != null && clientConfig.getCapacity() != null && clientConfig.getRefillRate() != null) {
            return new EndpointConfig(clientConfig.getCapacity(), clientConfig.getRefillRate());
        }

        // 3. Global defaults
        return new EndpointConfig(defaultCapacity, defaultRefillRate);
    }

    public String normalizeEndpoint(String endpoint) {
        if (endpoint != null && endpoint.startsWith("/")) {
            return endpoint.substring(1);
        }
        return endpoint;
    }

    @Data
    public static class ClientConfig {
        private Integer capacity;
        private Double refillRate;
        private Map<String, EndpointConfig> endpoints = new HashMap<>();
    }

    @Data
    public static class EndpointConfig {
        private int capacity;
        private double refillRate;

        public EndpointConfig() {}

        public EndpointConfig(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
        }
    }
}
