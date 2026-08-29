package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimiterController.class)
class RateLimiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void checkRateLimit_whenAllowed_returns200WithHeaders() throws Exception {
        when(rateLimiterService.evaluate("premium", "/payments"))
                .thenReturn(new RateLimitResponse(true, 99, 100, 0));

        mockMvc.perform(post("/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"premium\",\"endPoint\":\"/payments\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(99))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(header().string("X-RateLimit-Limit", "100"))
                .andExpect(header().string("X-RateLimit-Remaining", "99"));
    }

    @Test
    void checkRateLimit_whenRejected_returns429WithRetryAfter() throws Exception {
        when(rateLimiterService.evaluate("free", "/payments"))
                .thenReturn(new RateLimitResponse(false, 0, 5, 3));

        mockMvc.perform(post("/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"free\",\"endPoint\":\"/payments\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3))
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(header().string("X-RateLimit-Limit", "5"));
    }

    @Test
    void checkRateLimit_blankClientId_returns400() throws Exception {
        mockMvc.perform(post("/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"\",\"endPoint\":\"/payments\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkRateLimit_blankEndpoint_returns400() throws Exception {
        mockMvc.perform(post("/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"premium\",\"endPoint\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkRateLimit_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
