# Softpay Fraud Detection Service — Architectural Design Considerations

This document details the architectural decisions, operational patterns, scalability strategies, and security controls engineered for the **Softpay Real-Time Fraud Detection Service**, emphasizing the **Valkey & PostgreSQL Polyglot Persistence Model**.

---

## 1. Polyglot Persistence Model: Valkey & PostgreSQL

In high-throughput, low-latency financial systems, applying a single database across all workloads creates fundamental trade-offs. The Softpay fraud engine adopts a **Polyglot Persistence Model**, decoupling transactional master data from high-frequency in-memory sliding windows:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Softpay Fraud Engine                            │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
                    ▼                                ▼
       ┌────────────────────────┐       ┌────────────────────────┐
       │   Tier 1: Valkey       │       │   Tier 2: PostgreSQL   │
       │   (Distributed Cache)  │       │   (Relational RDBMS)   │
       ├────────────────────────┤       ├────────────────────────┤
       │ • Card Velocity        │       │ • Terminal Registry    │
       │ • Terminal Velocity    │       │ • Merchant Profiles    │
       │ • Sliding Windows      │       │ • Rule Configurations  │
       │ • TTL Key Pruning      │       │ • Flyway Migrations    │
       │ • Latency: < 1ms       │       │ • ACID Durability      │
       └────────────────────────┘       └────────────────────────┘
```

### Tier 1: Valkey (Linux Foundation Open-Source In-Memory Store)
- **Role**: Sliding-window velocity tracking for cards and payment terminals.
- **Protocol**: Wire-compatible with Redis (RESP2 / RESP3), utilizing Spring Data Redis / Lettuce connection pooling.
- **Data Structure**: **Sorted Sets (`ZSET`)**:
  - Key: `fraud:velocity:term:{terminalId}` and `fraud:velocity:card:{hashedPan}`
  - Score: Epoch millisecond timestamp of the transaction
  - Member: Unique identifier combining UUID and timestamp
- **Operations**:
  - `ZCOUNT key from to`: Evaluates current transaction frequency within the time window.
  - `ZADD key score member`: Records incoming inflight transactions atomically.
  - `EXPIRE key 300`: Sets rolling 5-minute TTL to reclaim memory automatically.
  - `ZREMRANGEBYSCORE key 0 pruneHorizon`: Prunes expired timestamps during writes.
- **Performance**: Provides consistent sub-millisecond round-trip response times ($< 1\text{ms}$), fitting comfortably inside the $\le 10\text{ms}$ fraud evaluation SLA.

### Tier 2: PostgreSQL (Relational Master Data & Audit)
- **Role**: Authoritative store for payment terminal provisioning, merchant risk ratings, and configuration parameters.
- **Schema Management**: Managed via **Flyway** migrations (`V1__create_terminals_table.sql`).
- **Data Integrity**: Enforces relational integrity, constraints, and ACID transactions for state modifications.

---

## 2. Architecture & Extensible Rule Engine Design

### Strategy + Chain of Responsibility Pattern
The core fraud engine adopts a hybrid **Strategy + Chain of Responsibility** pattern:
- **Strategy Interface (`FraudRule`)**: Encapsulates discrete evaluation logic, isolating business rules from each other and from the orchestration service.
- **Ordered Chain (`@Order`)**: Beans implementing `FraudRule` are discovered automatically by Spring's dependency injection container and injected into `FraudDetectionService` in deterministic priority order.

### Compliance with the Open/Closed Principle (OCP)
The system is strictly open for extension but closed for modification:
- Adding a new rule (e.g., `GeoVelocityRule` or `BinAttackRule`) requires creating a single class implementing `FraudRule` annotated with `@Component` and `@Order`.
- Zero changes are required in `FraudDetectionService`, `FraudDetectionController`, or existing rules.

### Evolution Toward Dynamic Rule Reloading
1. **Hot Configuration Updates**: Using Spring Cloud Config or Consul KV with `@RefreshScope` to alter rule thresholds (`baseMaxAmountEur`, `windowSeconds`, `threatDeductionFactor`) without restarts.
2. **Database-Driven Rule Ensembles**: Storing rule status (`ENABLED`, `SHADOW`, `DISABLED`), dynamic weights, and threshold bounds in PostgreSQL, loaded into local memory cache (Caffeine) with Change-Data-Capture (Debezium/Kafka) invalidation.
3. **External Expression Engines**: Embedding Google CEL (Common Expression Language) for dynamic runtime expressions without compilation overhead.

---

## 3. High Availability, Scalability & Sub-10ms Latency

### Stateless Horizontal Scaling on Kubernetes
The fraud evaluation service is inherently stateless outside of velocity tracking. When deployed on Kubernetes (or Nomad):
- **Horizontal Pod Autoscaling (HPA)**: Scales pods dynamically based on CPU utilization and incoming HTTP request rates (`http_requests_per_second`).
- **Virtual Threads (Project Loom)**: By enabling `spring.threads.virtual.enabled=true` on Java 21+, Tomcat allocates lightweight virtual threads per incoming request. Platform threads remain unblocked during telemetry dispatch or downstream lookups, enabling tens of thousands of concurrent requests with low memory footprint.

### Atomic Evaluation via Valkey Lua Scripts
To eliminate race conditions between reading velocity and recording the inflight transaction across distributed pods:

```lua
-- KEYS[1]: Velocity Key (e.g. fraud:velocity:card:<hash>)
-- ARGV[1]: Current Timestamp (Epoch ms)
-- ARGV[2]: Window Start Timestamp (Epoch ms)
-- ARGV[3]: Max Allowed Transactions in Window
-- ARGV[4]: Key TTL in seconds
-- ARGV[5]: Transaction ID / Nonce

-- 1. Evict entries outside the sliding window
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])

-- 2. Count active transactions in the sliding window
local currentCount = redis.call('ZCARD', KEYS[1])

-- 3. Evaluate breach
if currentCount >= tonumber(ARGV[3]) then
    return { 0, currentCount }
else
    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[5])
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
    return { 1, currentCount }
end
```

---

## 4. Resilience, Fault Tolerance & Degradation Strategies

Inflight payment transactions carry strict end-to-end SLAs (typically 50ms–100ms total budget for payment gateway switches; fraud service allocation $\le 10\text{ms}$).

### Circuit Breakers, Bulkheads & Timeouts (Resilience4j)
- **Timeouts**: Every external network call (e.g., Valkey cluster, PostgreSQL pool) is capped with a strict timeout ($\le 5\text{ms}$).
- **Bulkheads**: Protects system resources by restricting concurrent outbound calls to auxiliary services, preventing thread exhaustion.
- **Circuit Breakers**: When error rates exceed 50% over a 10-second rolling window, the circuit opens immediately, bypassing the faulty component.

### Fail-Closed vs. Fail-Open vs. Degraded Stateless Mode

| Strategy | Behavior on Failure | Pros | Cons / Risks |
| :--- | :--- | :--- | :--- |
| **Fail-Closed** | Reject all transactions when dependency is down. | Zero financial fraud loss. | Catastrophic merchant disruption; massive customer insult rate. |
| **Fail-Open** | Approve all transactions unconditionally when dependency fails. | Zero friction for legitimate shoppers. | Vulnerable to coordinated fraud rings during outages (e.g., card testing bursts). |
| **Degraded Stateless Mode (Recommended)** | Fall back to local, stateless rules (known terminals, single-tx amount vs. threat score) while skipping cross-node velocity checks. | High conversion maintained while still blocking egregious high-amount/high-threat attempts. | Temporary vulnerability to multi-terminal velocity attacks during the outage window. |

#### Softpay Hybrid Fallback Policy
1. **Low Risk ($< 50\text{ EUR}$, terminal threat score $< 20$)**: **Fail-Open** with an asynchronous audit flag (`status=DEGRADED_ALLOWED`).
2. **Medium Risk ($50 - 500\text{ EUR}$)**: Evaluate remaining stateless rules (`UnknownTerminalRule`, `AmountVsThreatScoreRule`); if passed, allow with risk warning.
3. **High Risk ($> 500\text{ EUR}$ or terminal threat score $\ge 70$)**: **Fail-Closed** or route to mandatory **3DS Challenge / Step-Up Authentication**.

---

## 5. Security & PCI-DSS v4.0 Compliance

Operating in proximity to payment switches demands strict adherence to **PCI-DSS v4.0 Requirement 3 (Protect Cardholder Data)**:

### 1. Primary Account Number (PAN) Protection & Descoping
- **Zero Plaintext Logging**: Raw PANs are never logged. The `TransactionEvaluationRequest.toString()` implementation automatically applies `CardSecurityUtils.maskPan(...)` (`453201******1111`).
- **Cryptographic Tokenization / Salted Hashing**:
  - Plain SHA-256 is vulnerable to rainbow table attacks because the valid Luhn 16-digit PAN space is small ($< 10^{16}$).
  - Card velocity tracking utilizes **HMAC-SHA-256** or salted `SHA-256` using an application secret stored in **HashiCorp Vault** or cloud KMS. Plain PANs never touch Valkey keys.
- **Zero SAD Storage**: The fraud service never receives, caches, or logs Sensitive Authentication Data (SAD) such as CVV2/CVC2, PIN blocks, or raw magstripe track data.

### 2. Transport & API Gateway Security
- **Mutual TLS (mTLS)**: Enforced for all inter-service communication between Softpay payment gateways, acquiring switches, and the fraud microservice.
- **Service-to-Service Authentication**: OAuth2 Client Credentials or signed JWTs with short expiry (5 minutes) and strict audience validation.
- **Input Sanitization**: Strict Jakarta Validation constraints on all request fields prevent buffer manipulation, SQL/NoSQL injection, and malformed payload attacks.

---

## 6. Observability, Auditing & Monitoring

### Structured JSON Logging & Tracing
Using Logback with Logstash JSON Encoder:
- Every log statement contains structured metadata (`correlationId`, `terminalId`, `amountEur`, `maskedPan`, `status`, `fraudScore`).
- `CorrelationIdFilter` reads or generates `X-Correlation-ID` and binds it to the SLF4J `MDC` context, returning it in the HTTP response header for distributed tracing across services.

### Prometheus & Grafana RED Metrics
Custom metrics registered via Micrometer:
1. **`fraud.evaluations.total` (Counter)**: Tagged with `status={ALLOWED|REJECTED}` to monitor rejection rate anomalies.
2. **`fraud.evaluation.duration` (Timer)**: Tracks execution latency percentiles ($p50, p95, p99, p99.9$). Alerts trigger if $p99 > 15\text{ms}$.
3. **`fraud.rule.triggers.total` (Counter)**: Tagged with individual rule names to identify rule drift.

### Asynchronous Audit Logging via Kafka
Post-evaluation, transaction contexts and rule outputs are published asynchronously to a high-throughput Kafka topic (`fraud-evaluation-audit`):
- Non-blocking: Uses `CompletableFuture` so publishing does not impact evaluation latency.
- Serves as the golden source for compliance auditing, fraud operations investigation, and offline model training.

---

## 7. Framework Choices & Production Roadmap

- **Java 21 / 25**: Virtual Threads (Project Loom) for high-concurrency non-blocking I/O; Java Records for immutable, allocation-efficient domain events and DTOs.
- **Spring Boot 3.3**: Native integration with Micrometer 1.13 observation APIs, Springdoc OpenAPI v3, and RFC 7807 `ProblemDetail`.
- **Roadmap Enhancements**:
  1. **Impossible Travel / Geo-Velocity Rule**: Haversine formula calculation over IP / GPS locations.
  2. **Shadow-Mode Machine Learning Pipeline**: ONNX Runtime executing LightGBM/XGBoost models in parallel with deterministic rules.
  3. **Apache AGE / Neo4j Graph Ring Detection**: Asynchronously mapping shared card numbers, terminal IDs, and merchant accounts into a graph store to detect organized syndicates.
