package com.ratelimiter.rate_limiter_service.model;

import jakarta.validation.constraints.NotBlank;

public record RateLimitRequest(
        @NotBlank(message = "clientId must not be blank") String clientId,
        @NotBlank(message = "endPoint must not be blank") String endPoint
) {}