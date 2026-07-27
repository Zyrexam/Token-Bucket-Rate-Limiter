package com.ratelimiter.rate_limiter_service.model;

public record RateLimitResponse(
        boolean allowed,
        int remaining,
        int limit,
        long retryAfterSeconds
) {}