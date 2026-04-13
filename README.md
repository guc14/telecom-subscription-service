# Telecom Subscription Service

A production-pattern RESTful backend for managing telecom customer subscriptions,
built with Java 21 / Spring Boot 3 + a Python FastAPI AI advisor sidecar.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4 |
| Persistence | Spring Data JPA, MySQL 8, H2 (tests) |
| Cache | Redis (customer lookup, idempotency keys) |
| Messaging | Apache Kafka (subscription activation events) |
| AI | OpenAI GPT-4o-mini, Python FastAPI sidecar |
| Cross-cutting | Spring AOP (request logging) |
| API Docs | Swagger / OpenAPI 3 |
| DevOps | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc, EmbeddedKafka |

---

## Architecture

```
Browser / Postman
      │
      │  REST (port 8080)
      ▼
┌──────────────────────────────────────────┐
│  Java Spring Boot                        │
│  CustomerController                      │
│  ServicePlanController  ──── Redis ────► │  Cache + Idempotency Keys
│  AdvisorController                       │
│         │                                │
│         ▼                                │
│  SubscriptionService                     │
│    1. Redis idempotency check            │
│    2. Business rule validation           │
│    3. DB write (MySQL)                   │
│    4. Redis key store (24h TTL)          │
│    5. Kafka publish ──────────────────►  │  subscription.activated topic
│                                          │         │
│  SubscriptionNotificationConsumer ◄──────┘         │
│    → SMS notification (simulated)                  │
│    → CRM update (simulated)                        │
│    → Audit log (simulated)                         │
└──────────┬───────────────────────────────┘
           │  POST /api/v1/advise
           ▼
┌──────────────────────────────────────────┐
│  Python FastAPI Sidecar (port 8000)      │
│  TelecomAdvisorAgent                     │◄── OpenAI GPT-4o-mini
│    Tool: get_subscribed_plans            │
│    Tool: get_all_plans                   │
│    Tool: get_subscription_details        │
└──────────┬───────────────────────────────┘
           │  GET /plans, GET /plans/by-customer/{id}
           ▼  (calls back to Java)
    Java Spring Boot (same service)
```

---

## Quick Start

### Option 1: Full stack (Docker Compose)

```bash
export OPENAI_API_KEY=sk-your-key-here
docker-compose up --build
```

- Java API + Swagger: http://localhost:8080/swagger-ui.html
- Python sidecar docs: http://localhost:8000/docs
- H2 console: http://localhost:8080/h2-console

### Option 2: Tests only (no external services needed)

```bash
mvn test
# Uses H2 (no MySQL), cache disabled (no Redis), EmbeddedKafka (no Kafka)
```

---

## API Endpoints

### Customer Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /customers | List all customers |
| GET | /customers/{id} | Get by ID **(Redis cached, TTL 10 min)** |
| POST | /customers | Create customer |
| PUT | /customers/{id} | Update customer (evicts cache) |
| DELETE | /customers/{id} | Delete customer (evicts cache) |
| GET | /customers/page | Paginated list |
| GET | /customers/search | Filter by name keyword + age range (DB-level) |
| GET | /customers/{id}/profile | Billing profile |
| POST | /customers/{id}/profile | Create billing profile |
| PUT | /customers/{id}/profile | Update billing profile |
| DELETE | /customers/{id}/profile | Delete billing profile |

### Service Plan Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /plans | List all plans |
| GET | /plans/{id} | Get plan by ID |
| POST | /plans | Create plan (AI description if omitted) |
| PUT | /plans/{id} | Update plan |
| DELETE | /plans/{id} | Delete plan |

### Subscription Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /plans/{planId}/customers/{customerId}/activate | **Activate subscription (idempotent)** |
| GET | /plans/{planId}/customers | List customers on a plan |
| GET | /plans/{planId}/customers/search | Search customers on plan (paginated, DB-level) |
| GET | /plans/by-customer/{customerId} | Plans a customer is on |
| GET | /plans/subscriptions/by-customer/{customerId} | Full subscription history |

### AI Advisor

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /advisor/customers/{id}?question=... | AI plan recommendation |

---

## Key Design Decisions

### 1. Idempotent Subscription Activation (Redis + DB)

**Problem:** Mobile apps and retry logic frequently send duplicate activation requests
(network timeout → retry, double-tap → two requests). Naive implementations create
duplicate subscriptions.

**Solution:** Two-layer idempotency:

```
Layer 1 (Redis — fast path):
  Client sends X-Idempotency-Key: <uuid> header
  On first request: store "idempotency:subscription:<key>" in Redis, TTL 24h
  On retry: Redis.hasKey() returns true → return 200 immediately, no DB touch

Layer 2 (DB — safety net):
  Unique constraint on (customer_id, plan_id)
  Catches any duplicate that slips through (e.g. Redis key expired between crash + retry)
```

This is the exact pattern used by Stripe's payment API and Twilio's messaging API.

**Trade-off:** 24h TTL means a client cannot re-use the same key for a legitimately
different activation within 24 hours. In practice, clients generate a fresh UUID per
activation attempt — this is never an issue.

---

### 2. Kafka Async Event Publishing (Non-blocking)

**Problem:** After subscription activation, downstream systems need to be notified:
SMS/email confirmation, CRM update, audit log, billing cycle creation. Doing these
synchronously in the HTTP handler would:
  - Make activation latency depend on the slowest downstream system
  - Couple the core transaction to non-critical workflows
  - Risk rollback if a notification fails

**Solution:** After DB commit, publish a `SubscriptionActivatedEvent` to Kafka topic
`subscription.activated`. The HTTP response returns immediately. A separate consumer
handles downstream workflows asynchronously.

```
Producer config:
  acks=all + enable.idempotence=true
  → Strongest durability + no broker-level duplicates on retry

Partition key = customerId
  → All events for one customer land on the same partition
  → Consumer processes events in activation order per customer
```

**Fallback:** Kafka publish failure is logged but does not roll back the subscription.
The core transaction is already committed. In production, an outbox pattern would
guarantee delivery — appropriate addition for a senior-level role.

---

### 3. Redis Customer Cache (Hot Path Optimisation)

`GET /customers/{id}` is called on every subscription activation to verify existence
and retrieve the customer name for the Kafka event payload. Under concurrent activation
load (e.g. promotional campaign), this becomes the most-read endpoint.

Cache key: `customers::{id}` | TTL: 10 min | Serialisation: JSON (human-readable in redis-cli)

Evicted on PUT and DELETE via `@CacheEvict` — no stale reads.

Why only cache by ID (not lists): List endpoints change on every write. Caching a list
requires full invalidation on every create/update — cache coherence cost exceeds the
read benefit. Per-entity caching by ID is the correct starting point.

---

### 4. DB-Level Paginated Filtering

`GET /plans/{planId}/customers/search` filters subscribers by name keyword + age range.

**Rejected approach:**
```java
// Load ALL subscriptions into memory, then filter in Java
List<Subscription> all = subscriptionRepository.findByPlanId(planId);
return all.stream().filter(s -> s.getCustomer().getName().contains(keyword))...
```
With 50,000 subscribers on a popular plan, this loads 50,000 records into the JVM heap
to return page 1 of 10.

**Chosen approach:** Single JPQL query with `WHERE`, `LIKE`, `LIMIT/OFFSET` — only the
requested page is ever transferred from DB to application layer.

The `:keyword IS NULL OR ...` conditional pattern handles "no filter" without separate
repository methods per filter combination.

---

### 5. AOP Request Logging

A single `@Around` advice in `LoggingAspect` instruments all controller methods:
entry (method + args), exit (execution time), error (exception + time).

Adding a new controller automatically gets logging — zero developer effort.

**Trade-off:** AOP adds indirection. Appropriate only for genuinely cross-cutting
concerns (logging, metrics, security). Business logic (capacity checks, idempotency)
belongs in the service layer, never in an aspect.

---

### 6. AI Advisor — Python Sidecar with Tool-Calling Agent

A separate Python FastAPI microservice exposes `POST /api/v1/advise`. The Java
`AdvisorController` calls this sidecar when a customer requests plan recommendations.

Inside the sidecar, `TelecomAdvisorAgent` runs an OpenAI function-calling loop:
the LLM autonomously decides to call `get_subscribed_plans` → `get_all_plans` →
synthesises a recommendation. The loop is capped at MAX_STEPS=5.

**Why Python sidecar, not Java?**
  - Richer AI/ML ecosystem (OpenAI SDK, async httpx, Pydantic)
  - AI logic scales independently — no need to scale DB-connected Java pods for AI load
  - Swap AI backend without touching Java

**Fallback:** If sidecar is down → Java catches `ResourceAccessException` → returns 503.
Core subscription endpoints are completely unaffected.

---

## Testing Strategy

### Unit Tests (Mockito, no Spring context)
`SubscriptionServiceTest` — 7 cases covering all business rule branches:
  - Idempotency key hit → skip, no DB write
  - Null idempotency key → no Redis check, proceed
  - Customer not found → 404
  - Plan not found → 404
  - Duplicate subscription → 409
  - Plan at capacity → 409
  - Happy path → verify DB save + Redis key stored + Kafka event published

### Integration Tests (SpringBootTest + EmbeddedKafka)
`SubscriptionControllerIntegrationTest` — full stack, real HTTP via MockMvc:
  - H2 in-memory DB (no MySQL needed)
  - Redis cache disabled in test profile
  - EmbeddedKafka — real Kafka broker in-process, no external Kafka
  - Full lifecycle: create customer → create plan → activate → verify responses
