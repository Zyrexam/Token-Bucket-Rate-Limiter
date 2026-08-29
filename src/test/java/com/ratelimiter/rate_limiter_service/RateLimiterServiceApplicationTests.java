package com.ratelimiter.rate_limiter_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ratelimiter.rate_limiter_service.config.RateLimiterProperties;
import com.ratelimiter.rate_limiter_service.controller.RateLimiterController;
import com.ratelimiter.rate_limiter_service.model.RateLimitRequest;
import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;

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

		var response = service.evaluate("test-client", "");
		assertTrue(response.allowed());
		assertEquals(10, response.remaining());
	}

	@Test
	void controllerReturns429WhenBlocked() {
		var service = mock(RateLimiterService.class);
		when(service.evaluate("limited-client", ""))
				.thenReturn(new RateLimitResponse(false, 0, 5, 30));

		var controller = new RateLimiterController(service);
		var response = controller.checkRateLimit(new RateLimitRequest("limited-client", ""));

		assertEquals(429, response.getStatusCode().value());
		assertEquals("30", response.getHeaders().getFirst("Retry-After"));
	}

	@Test
	void controllerReturns200WhenAllowed() {
		var service = mock(RateLimiterService.class);
		when(service.evaluate("ok-client", ""))
				.thenReturn(new RateLimitResponse(true, 4, 5, 0));

		var controller = new RateLimiterController(service);
		var response = controller.checkRateLimit(new RateLimitRequest("ok-client", ""));

		assertEquals(200, response.getStatusCode().value());
		assertEquals("4", response.getHeaders().getFirst("X-RateLimit-Remaining"));
	}
}
