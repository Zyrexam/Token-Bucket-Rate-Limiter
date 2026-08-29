package com.ratelimiter.rate_limiter_service.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import com.ratelimiter.rate_limiter_service.config.RateLimiterProperties;
import com.ratelimiter.rate_limiter_service.model.RateLimitResponse;


@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;
    private DefaultRedisScript<List<Object>> rateLimitScript;

    public RateLimiterService(StringRedisTemplate redisTemplate,
                              RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        script();     }


    private DefaultRedisScript<List<Object>> script() {
        if (rateLimitScript != null) return rateLimitScript;
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setLocation(new ClassPathResource("scripts/rate_limiter.lua"));
        rateLimitScript.setResultType((Class) List.class);
        return rateLimitScript;
    }


    public RateLimitResponse evaluate(String clientId, String endPoint) {

        RateLimiterProperties.EndpointConfig config = properties.resolveForClientAndEndpoint(clientId, endPoint);
        int capacity = config.getCapacity();
        double refillRate = config.getRefillRate();
        long nowMillis = Instant.now().toEpochMilli();


        String normalizedEndpoint = properties.normalizeEndpoint(endPoint);
        String redisKey = "bucket:" + clientId + ":" + normalizedEndpoint;

        try {
            List<Object> result = redisTemplate.execute(
                    script(),
                    List.of(redisKey),
                    String.valueOf(refillRate),
                    String.valueOf(capacity),
                    String.valueOf(nowMillis)
            );

            boolean allowed = Long.parseLong(result.get(0).toString()) == 1L;
            int remaining = (int) Math.floor(Double.parseDouble(result.get(1).toString()));
            long retryAfter = (long) Double.parseDouble(result.get(2).toString());

            return new RateLimitResponse(allowed, remaining, capacity, retryAfter);

        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed for client '{}'", clientId, e);

            if (properties.isFailOpen()) {
                log.warn("Fail-open enabled: allowing request for client '{}'", clientId);
                return new RateLimitResponse(true, capacity, capacity, 0);
            } else {
                log.warn("Fail-close enabled: rejecting request for client '{}'", clientId);
                return new RateLimitResponse(false, 0, capacity, 0);
            }
        }
    }
}