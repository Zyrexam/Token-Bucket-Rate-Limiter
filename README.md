# Rate Limiter Service

Token bucket rate limiter backed by Redis + Lua. Configurable per-client, per-endpoint limits.

## Stack

- Spring Boot 4.1 / Java 26
- Redis 7 (Lettuce client)
- Docker Compose

## Quick Start

```bash
docker compose up -d          # Redis on 6379
mvn package -DskipTests
java -jar target/rate-limiter-service-0.0.1-SNAPSHOT.jar
```

## API

```
POST /v1/check
Content-Type: application/json

{
  "clientId": "free",
  "endPoint": "/payments"
}
```

**Response:**
```json
{
  "allowed": true,
  "remaining": 4,
  "limit": 5,
  "retryAfterSeconds": 0
}
```

**Headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After` (on 429)

**HTTP Status:** `200` allowed, `429` rate limited, `400` validation error

## Tiers (`application.properties`)

| Tier | Endpoint | Capacity | Refill Rate |
|------|----------|----------|-------------|
| free | /payments | 5 | 1/sec |
| free | (other) | 10 | 2/sec |
| premium | /payments | 100 | 10/sec |
| premium | /notifications | 10 | 1/sec |
| premium | (other) | 50 | 5/sec |
| unknown | (any) | 10 | 2/sec |

## Config

```properties
rate-limiter.default-capacity=10
rate-limiter.default-refill-rate=2.0
rate-limiter.fail-open=true          # allow if Redis is down
```

Priority: endpoint > client-level > global default.

## Tests

```bash
mvn test                # 31 unit tests
docker compose up -d    # then run test-e2e.ps1 for E2E
```

## How It Works

Each request runs a Lua script atomically in Redis — reads tokens + last refill time, refills based on elapsed time, consumes one token, writes back. No race conditions even under parallel load.
