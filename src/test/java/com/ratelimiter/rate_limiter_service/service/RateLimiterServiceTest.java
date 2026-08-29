package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.config.RateLimiterProperties;
import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimiterProperties properties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();

        // Set up premium client with endpoint overrides
        RateLimiterProperties.ClientConfig premiumConfig = new RateLimiterProperties.ClientConfig();
        premiumConfig.setCapacity(50);
        premiumConfig.setRefillRate(5.0);

        RateLimiterProperties.EndpointConfig paymentsEndpoint =
                new RateLimiterProperties.EndpointConfig(100, 10.0);
        RateLimiterProperties.EndpointConfig notificationsEndpoint =
                new RateLimiterProperties.EndpointConfig(10, 1.0);

        premiumConfig.getEndpoints().put("payments", paymentsEndpoint);
        premiumConfig.getEndpoints().put("notifications", notificationsEndpoint);

        properties.getClients().put("premium", premiumConfig);

        // Set up basic client (no client-level defaults, only endpoint override)
        RateLimiterProperties.ClientConfig basicConfig = new RateLimiterProperties.ClientConfig();
        RateLimiterProperties.EndpointConfig basicPayments =
                new RateLimiterProperties.EndpointConfig(5, 1.0);
        basicConfig.getEndpoints().put("payments", basicPayments);

        properties.getClients().put("free", basicConfig);

        rateLimiterService = new RateLimiterService(redisTemplate, properties);
        rateLimiterService.init();
    }

    // --- Endpoint-specific config (highest priority) ---

    @Test
    void evaluate_premiumClient_paymentsEndpoint_usesEndpointConfig() {
        List<Object> mockResult = Arrays.asList(1L, "99.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/payments");

        assertTrue(response.allowed());
        assertEquals(100, response.limit());  // endpoint-specific: 100
        assertEquals(99, response.remaining());
    }

    @Test
    void evaluate_premiumClient_notificationsEndpoint_usesEndpointConfig() {
        List<Object> mockResult = Arrays.asList(1L, "9.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/notifications");

        assertTrue(response.allowed());
        assertEquals(10, response.limit());  // endpoint-specific: 10
        assertEquals(9, response.remaining());
    }

    // --- Client-level defaults (medium priority) ---

    @Test
    void evaluate_premiumClient_unknownEndpoint_usesClientDefaults() {
        List<Object> mockResult = Arrays.asList(1L, "49.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/search");

        assertTrue(response.allowed());
        assertEquals(50, response.limit());  // client-level default: 50
    }

    // --- Global defaults (lowest priority) ---

    @Test
    void evaluate_basicClient_unknownEndpoint_usesGlobalDefaults() {
        List<Object> mockResult = Arrays.asList(1L, "9.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("free", "/search");

        assertTrue(response.allowed());
        assertEquals(10, response.limit());  // global default: 10
    }

    @Test
    void evaluate_unknownClient_anyEndpoint_usesGlobalDefaults() {
        List<Object> mockResult = Arrays.asList(1L, "9.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("random-user", "/anything");

        assertTrue(response.allowed());
        assertEquals(10, response.limit());  // global default: 10
    }

    // --- Rejected request ---

    @Test
    void evaluate_whenRejected_returns429Info() {
        List<Object> mockResult = Arrays.asList(0L, "0.0", 3L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/notifications");

        assertFalse(response.allowed());
        assertEquals(0, response.remaining());
        assertEquals(10, response.limit());
        assertEquals(3, response.retryAfterSeconds());
    }

    // --- Redis failure ---

    @Test
    void evaluate_redisDown_failOpen_returnsAllowed() {
        properties.setFailOpen(true);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection refused"));

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/payments");

        assertTrue(response.allowed());
    }

    @Test
    void evaluate_redisDown_failClose_returnsRejected() {
        properties.setFailOpen(false);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection refused"));

        RateLimitResponse response = rateLimiterService.evaluate("premium", "/payments");

        assertFalse(response.allowed());
    }

    // --- Redis key format verification ---

    @Test
    void evaluate_redisKey_includesClientIdAndEndpoint() {
        List<Object> mockResult = Arrays.asList(1L, "9.0", 0L);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(mockResult);

        rateLimiterService.evaluate("premium", "/payments");

        // Verify the key passed to Redis is "bucket:premium:payments"
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("bucket:premium:payments")),
                any(), any(), any()
        );
    }
}
