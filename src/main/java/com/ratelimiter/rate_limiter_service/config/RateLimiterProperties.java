package com.ratelimiter.rate_limiter_service.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private int defaultCapacity = 10;
    private double defaultRefillRate = 2.0;
    private boolean failOpen = true;
    private Map<String, ClientConfig> clients = new HashMap<>();

    // --- Getters and Setters ---

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public double getDefaultRefillRate() {
        return defaultRefillRate;
    }

    public void setDefaultRefillRate(double defaultRefillRate) {
        this.defaultRefillRate = defaultRefillRate;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public Map<String, ClientConfig> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientConfig> clients) {
        this.clients = clients;
    }

    public ClientConfig resolveForClient(String clientId) {
        ClientConfig config = clients.get(clientId);
        if (config != null) {
            return config;
        }
        // zero capacity/rate are accepted as explicit config, no fallback
        ClientConfig defaults = new ClientConfig();
        defaults.setCapacity(defaultCapacity);
        defaults.setRefillRate(defaultRefillRate);
        return defaults;
    }

    public static class ClientConfig {
        private int capacity;
        private double refillRate;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public double getRefillRate() {
            return refillRate;
        }

        public void setRefillRate(double refillRate) {
            this.refillRate = refillRate;
        }
    }
}