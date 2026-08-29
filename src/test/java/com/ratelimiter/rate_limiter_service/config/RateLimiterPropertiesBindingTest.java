package com.ratelimiter.rate_limiter_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the application.properties are correctly bound to RateLimiterProperties
 * and that resolveForClientAndEndpoint returns the expected configs.
 */
@SpringBootTest(
    properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    }
)
@Import(RateLimiterPropertiesBindingTest.TestRedisConfig.class)
class RateLimiterPropertiesBindingTest {

    @Autowired
    private RateLimiterProperties properties;

    @TestConfiguration
    static class TestRedisConfig {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            // Mock Redis template to avoid needing a real Redis connection
            return org.mockito.Mockito.mock(StringRedisTemplate.class);
        }
    }

    // --- Premium client - endpoint-specific configs ---

    @Test
    void premiumClient_paymentsEndpoint_usesEndpointConfig() {
        var config = properties.resolveForClientAndEndpoint("premium", "/payments");
        assertEquals(100, config.getCapacity(), "payments endpoint should have capacity=100");
        assertEquals(10.0, config.getRefillRate(), 0.001, "payments endpoint should have refillRate=10.0");
    }

    @Test
    void premiumClient_notificationsEndpoint_usesEndpointConfig() {
        var config = properties.resolveForClientAndEndpoint("premium", "/notifications");
        assertEquals(10, config.getCapacity(), "notifications endpoint should have capacity=10");
        assertEquals(1.0, config.getRefillRate(), 0.001, "notifications endpoint should have refillRate=1.0");
    }

    @Test
    void premiumClient_notificationsEndpointWithoutSlash_usesEndpointConfig() {
        var config = properties.resolveForClientAndEndpoint("premium", "notifications");
        assertEquals(10, config.getCapacity(), "notifications endpoint without slash should have capacity=10");
        assertEquals(1.0, config.getRefillRate(), 0.001, "notifications endpoint without slash should have refillRate=1.0");
    }

    // --- Premium client - client-level defaults ---

    @Test
    void premiumClient_unknownEndpoint_usesClientDefaults() {
        var config = properties.resolveForClientAndEndpoint("premium", "/search");
        assertEquals(50, config.getCapacity(), "unknown endpoint should use client-level capacity=50");
        assertEquals(5.0, config.getRefillRate(), 0.001, "unknown endpoint should use client-level refillRate=5.0");
    }

    @Test
    void premiumClient_genericEndpoint_usesClientDefaults() {
        var config = properties.resolveForClientAndEndpoint("premium", "/api/data");
        assertEquals(50, config.getCapacity(), "unknown endpoint should use client-level capacity=50");
        assertEquals(5.0, config.getRefillRate(), 0.001, "unknown endpoint should use client-level refillRate=5.0");
    }

    // --- Basic client - endpoint-specific configs ---

    @Test
    void basicClient_paymentsEndpoint_usesEndpointConfig() {
        var config = properties.resolveForClientAndEndpoint("free", "/payments");
        assertEquals(5, config.getCapacity(), "free payments should have capacity=5");
        assertEquals(1.0, config.getRefillRate(), 0.001, "free payments should have refillRate=1.0");
    }

    // --- Basic client - no client-level defaults -> global defaults ---

    @Test
    void basicClient_unknownEndpoint_usesGlobalDefaults() {
        var config = properties.resolveForClientAndEndpoint("free", "/search");
        assertEquals(10, config.getCapacity(), "free unknown endpoint should use global capacity=10");
        assertEquals(2.0, config.getRefillRate(), 0.001, "free unknown endpoint should use global refillRate=2.0");
    }

    // --- Unknown client - global defaults ---

    @Test
    void unknownClient_usesGlobalDefaults() {
        var config = properties.resolveForClientAndEndpoint("random-user", "/anything");
        assertEquals(10, config.getCapacity(), "unknown client should use global capacity=10");
        assertEquals(2.0, config.getRefillRate(), 0.001, "unknown client should use global refillRate=2.0");
    }

    // --- Client-level properties are correctly bound ---

    @Test
    void premiumClient_clientLevelCapacityIsBound() {
        var clientConfig = properties.getClients().get("premium");
        assertNotNull(clientConfig, "premium should exist in clients map");
        assertEquals(50, clientConfig.getCapacity(), "premium client-level capacity should be 50");
        assertEquals(5.0, clientConfig.getRefillRate(), 0.001, "premium client-level refillRate should be 5.0");
    }

    @Test
    void basicClient_clientLevelCapacityIsNull() {
        var clientConfig = properties.getClients().get("free");
        assertNotNull(clientConfig, "free should exist in clients map");
        assertNull(clientConfig.getCapacity(), "free should not have client-level capacity");
        assertNull(clientConfig.getRefillRate(), "free should not have client-level refillRate");
    }

    // --- Endpoints map is correctly populated ---

    @Test
    void premiumClient_endpointsMapHasCorrectEntries() {
        var clientConfig = properties.getClients().get("premium");
        assertNotNull(clientConfig, "premium should exist");
        assertEquals(2, clientConfig.getEndpoints().size(),
            "premium should have 2 endpoint configs (payments, notifications)");

        var payments = clientConfig.getEndpoints().get("payments");
        assertNotNull(payments, "payments endpoint should exist");
        assertEquals(100, payments.getCapacity());
        assertEquals(10.0, payments.getRefillRate(), 0.001);

        var notifications = clientConfig.getEndpoints().get("notifications");
        assertNotNull(notifications, "notifications endpoint should exist");
        assertEquals(10, notifications.getCapacity());
        assertEquals(1.0, notifications.getRefillRate(), 0.001);
    }

    @Test
    void basicClient_endpointsMapHasPaymentsOnly() {
        var clientConfig = properties.getClients().get("free");
        assertNotNull(clientConfig, "free should exist");
        assertEquals(1, clientConfig.getEndpoints().size(),
            "free should have 1 endpoint config (payments)");

        var payments = clientConfig.getEndpoints().get("payments");
        assertNotNull(payments, "payments endpoint should exist");
        assertEquals(5, payments.getCapacity());
        assertEquals(1.0, payments.getRefillRate(), 0.001);
    }

    // --- Global defaults ---

    @Test
    void globalDefaultsAreSet() {
        assertEquals(10, properties.getDefaultCapacity(), "global default capacity should be 10");
        assertEquals(2.0, properties.getDefaultRefillRate(), 0.001, "global default refillRate should be 2.0");
    }
}
