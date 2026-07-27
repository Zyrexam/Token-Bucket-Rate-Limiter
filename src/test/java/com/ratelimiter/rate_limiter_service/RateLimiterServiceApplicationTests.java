package com.ratelimiter.rate_limiter_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ratelimiter.rate_limiter_service.config.RateLimiterProperties;
import com.ratelimiter.rate_limiter_service.controller.RateLimiterController;
import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;

@WebMvcTest(RateLimiterController.class)
class RateLimiterServiceApplicationTests {

	@Test
	void contextLoads() {
		assertTrue(true);
	}

	@Test
	void serviceFailsOpenWhenRedisDown() {
		var props = new RateLimiterProperties();
		props.setDefaultCapacity(10);
		props.setDefaultRefillRate(2.0);
		props.setFailOpen(true);

		var redis = mock(StringRedisTemplate.class);
		when(redis.execute(any(), anyList(), anyString(), anyString(), anyString()))
				.thenThrow(new RedisConnectionFailureException("no redis"));

		var service = new RateLimiterService(redis, props);

		var response = service.evaluate("test-client");
		assertTrue(response.allowed());
		assertEquals(10, response.remaining());
	}

	@Test
	void controllerReturns429WhenBlocked() {
		var service = mock(RateLimiterService.class);
		when(service.evaluate("limited-client"))
				.thenReturn(new RateLimitResponse(false, 0, 5, 30));

		var controller = new RateLimiterController(service);
		var response = controller.checkRateLimit("limited-client");

		assertEquals(429, response.getStatusCode().value());
		assertEquals("30", response.getHeaders().getFirst("Retry-After"));
	}

	@Test
	void controllerReturns200WhenAllowed() {
		var service = mock(RateLimiterService.class);
		when(service.evaluate("ok-client"))
				.thenReturn(new RateLimitResponse(true, 4, 5, 0));

		var controller = new RateLimiterController(service);
		var response = controller.checkRateLimit("ok-client");

		assertEquals(200, response.getStatusCode().value());
		assertEquals("4", response.getHeaders().getFirst("X-RateLimit-Remaining"));
	}


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void checkRateLimit_whenAllowed_returns200WithHeaders() throws Exception {
        when(rateLimiterService.evaluate("test-client"))
                .thenReturn(new RateLimitResponse(true, 9, 10, 0));

        mockMvc.perform(post("/v1/check")
                        .param("client_id", "test-client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(9))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.retryAfterSeconds").value(0))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void checkRateLimit_whenRejected_returns429WithRetryAfter() throws Exception {
        when(rateLimiterService.evaluate("test-client"))
                .thenReturn(new RateLimitResponse(false, 0, 5, 3));

        mockMvc.perform(post("/v1/check")
                        .param("client_id", "test-client"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("Retry-After", "3"));
    }

    @Test
    void checkRateLimit_whenBlankClientId_returns400() throws Exception {
        mockMvc.perform(post("/v1/check")
                        .param("client_id", ""))
                .andExpect(status().isBadRequest());
    }
}
