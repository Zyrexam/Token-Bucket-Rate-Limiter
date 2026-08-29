package com.ratelimiter.rate_limiter_service.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ratelimiter.rate_limiter_service.model.RateLimitRequest;
import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/v1")
@Validated
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }


    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit( @Valid @RequestBody RateLimitRequest request) {

        RateLimitResponse response = rateLimiterService.evaluate(request.clientId(),        request.endPoint());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(response.limit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(response.remaining()));

        if (response.allowed()) {
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response);
        } else {
            headers.set("Retry-After", String.valueOf(response.retryAfterSeconds()));
            return ResponseEntity.status(429)
                    .headers(headers)
                    .body(response);
        }
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleValidation(ConstraintViolationException ex) {
        return Map.of("error", ex.getMessage());
    }
}
