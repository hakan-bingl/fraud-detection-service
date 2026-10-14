# Softpay Fraud Detection Service — Architectural Design Considerations

This document outlines the architectural decisions, operational patterns, scalability strategies, and security controls engineered for the **Softpay Real-Time Fraud Detection Service**.

---

## 1. Architecture & Extensible Rule Engine Design

### Strategy + Chain of Responsibility Pattern
The core fraud engine adopts a hybrid **Strategy + Chain of Responsibility** pattern:
- **Strategy Interface (`FraudRule`)**: Encapsulates discrete evaluation logic, isolating business rules from each other and from the orchestration service.
- **Ordered Chain (`@Order`)**: Beans implementing `FraudRule` are discovered automatically by Spring's dependency injection container and injected into `FraudDetectionService` in deterministic priority order.

```text
Incoming TransactionContext
          │
          ▼
   ┌──────────────┐
   │ Order 1 Rule │ ──► Score / Hard Reject ──┐
   └──────────────┘                           │
          │                                   │
          ▼                                   │
   ┌──────────────┐                           │
   │ Order 2 Rule │ ──► Score / Hard Reject ──┼──► Composite Score Aggregator
   └──────────────┘                           │    (Capped at 100, Threshold = 70)
          │                                   │
          ▼                                   │
   ┌──────────────┐                           │
   │ Order N Rule │ ──► Score / Hard Reject ──┘
   └──────────────┘
```

### Compliance with the Open/Closed Principle (OCP)
The system is strictly open for extension but closed for modification:
- Adding a new rule (e.g., `GeoVelocityRule` or `BinAttackRule`) requires creating a single class implementing `FraudRule` annotated with `@Component` and `@Order`.
- Zero changes are required in `FraudDetectionService`, `FraudDetectionController`, or existing rules.

### Evolution Toward Dynamic Rule Reloading & Rule Engines
While compiled Java rules provide sub-millisecond execution times and compile-time type safety, production environments benefit from dynamic adjustments:
1. **Hot Configuration Updates**: Using Spring Cloud Config or Consul KV with `@RefreshScope` to alter rule thresholds (`baseMaxAmountEur`, `windowSeconds`, `threatDeductionFactor`) without restarts.
2. **Database-Driven Rule Ensembles**: Storing rule status (`ENABLED`, `SHADOW`, `DISABLED`), dynamic weights, and threshold bounds in PostgreSQL, loaded into local memory cache (Caffeine) with Change-Data-Capture (Debezium/Kafka) invalidation.
3. **External Expression Engines**: For non-technical risk analysts, embedding Google CEL (Common Expression Language) or Spring Expression Language (SpEL) allows rule definitions like:
   ```cel
   transaction.amountEur > (properties.baseMaxAmount * (1.0 - terminal.threatScore / 100.0))
   ```
   CEL provides sandboxed, deterministic evaluation with near-zero overhead without the memory footprint of heavy rule engines like Drools.

---

## 2. High Availability, Scalability & Sub-10ms Latency

### Stateless Horizontal Scaling on Kubernetes
The fraud evaluation service is inherently stateless outside of velocity tracking. When deployed on Kubernetes (or Nomad):
- **Horizontal Pod Autoscaling (HPA)**: Scales pods dynamically based on CPU utilization and incoming HTTP request rates (`http_requests_per_second`).
- **Virtual Threads (Project Loom)**: By enabling `spring.threads.virtual.enabled=true` on Java 21+, Tomcat allocates lightweight virtual threads per incoming request. Platform threads remain unblocked during telemetry dispatch or downstream lookups, enabling tens of thousands of concurrent requests with low memory footprint.

### Distributed Sliding Windows with Redis Cluster (Sorted Sets)
The current in-memory implementation (`ConcurrentHashMap` with `ConcurrentLinkedDeque`) operates within a single JVM node. For multi-replica production clusters, state must be distributed.

We replace the in-memory repository with **Redis Cluster** using **Sorted Sets (`ZSET`)** where:
- **Key**: Entity identifier (e.g., `fraud:velocity:term:{terminalId}` or `fraud:velocity:card:{hashedPan}`).
- **Score**: Epoch millisecond timestamp of the transaction.
- **Member**: Unique transaction identifier / UUID.

#### Atomic Evaluation & Recording via Lua Script
To eliminate race conditions between reading velocity and recording the inflight transaction, both operations execute inside a single atomic Redis Lua script:

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
    -- Breached: return rejection signal and current count without recording
    return { 0, currentCount }
else
    -- Allowed: record current transaction and refresh TTL
    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[5])
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
    return { 1, currentCount }
end
```
**Benefits:**
- **Sub-2ms execution**: Redis executes Lua scripts natively in memory.
- **Zero synchronization drift**: No distributed locks or round-trip multi-command latency required.
- **Automatic memory reclamation**: `EXPIRE` ensures dormant keys are automatically purged by Redis.

---

## 3. Resilience, Fault Tolerance & Degradation Strategies

Inflight payment transactions carry strict end-to-end SLAs (typically 50ms–100ms total budget for payment gateway switches; fraud service allocation $\le 10\text{ms}$).

### Circuit Breakers, Bulkheads & Timeouts (Resilience4j)
- **Timeouts**: Every external network call (e.g., Redis cluster, terminal lookup service) is capped with a strict timeout ($\le 5\text{ms}$).
- **Bulkheads**: Protects system resources by restricting concurrent outbound calls to auxiliary services, preventing thread exhaustion.
- **Circuit Breakers**: When error rates exceed 50% over a 10-second rolling window, the circuit opens immediately, bypassing the faulty component.

### Fail-Closed vs. Fail-Open vs. Degraded Stateless Mode

When an infrastructure dependency (e.g., Redis Cluster or external Threat Intelligence feed) fails or times out, the system must balance financial risk against merchant conversion rates:

| Strategy | Behavior on Failure | Pros | Cons / Risks |
| :--- | :--- | :--- | :--- |
| **Fail-Closed** | Reject all transactions when dependency is down. | Zero financial fraud loss. | Catastrophic merchant disruption; massive customer insult rate; revenue loss for Softpay. |
| **Fail-Open** | Approve all transactions unconditionally when dependency fails. | Zero friction for legitimate shoppers. | Vulnerable to coordinated fraud rings during outages (e.g., card testing bursts). |
| **Degraded Stateless Mode (Recommended)** | Fall back to local, stateless rules (known terminals, single-tx amount vs. threat score) while skipping cross-node velocity checks. | High conversion maintained while still blocking egregious high-amount/high-threat attempts. | Temporary vulnerability to multi-terminal velocity attacks during the outage window. |

#### Softpay Hybrid Fallback Policy
Softpay implements a risk-tier degradation policy:
1. **Low Risk ($< 50\text{ EUR}$, terminal threat score $< 20$)**: **Fail-Open** with an asynchronous audit flag (`status=DEGRADED_ALLOWED`).
2. **Medium Risk ($50 - 500\text{ EUR}$)**: Evaluate remaining stateless rules (`UnknownTerminalRule`, `AmountVsThreatScoreRule`); if passed, allow with risk warning.
3. **High Risk ($> 500\text{ EUR}$ or terminal threat score $\ge 70$)**: **Fail-Closed** or route to mandatory **3DS Challenge / Step-Up Authentication**.

---

## 4. Security & PCI-DSS v4.0 Compliance

Operating in proximity to payment switches demands strict adherence to **PCI-DSS v4.0 Requirement 3 (Protect Cardholder Data)**:

### 1. Primary Account Number (PAN) Protection & Descoping
- **Zero Plaintext Logging**: Raw PANs are never logged. The `TransactionEvaluationRequest.toString()` implementation automatically applies `CardSecurityUtils.maskPan(...)`:
  ```java
  // Example: 4532015112831111 -> 453201******1111
  String bin = clean.substring(0, 6);
  String last4 = clean.substring(clean.length() - 4);
  ```
- **Cryptographic Tokenization / Salted Hashing**:
  - Plain SHA-256 is vulnerable to rainbow table attacks because the valid Luhn 16-digit PAN space is small ($< 10^{16}$).
  - Card velocity tracking utilizes **HMAC-SHA-256** or salted `SHA-256` using an application secret stored in **HashiCorp Vault** or cloud KMS. Plain PANs never touch velocity cache keys.
- **Zero SAD Storage**: The fraud service never receives, caches, or logs Sensitive Authentication Data (SAD) such as CVV2/CVC2, PIN blocks, or raw magstripe track data.

### 2. Transport & API Gateway Security
- **Mutual TLS (mTLS)**: Enforced for all inter-service communication between Softpay payment gateways, acquiring switches, and the fraud microservice.
- **Service-to-Service Authentication**: OAuth2 Client Credentials or signed JWTs with short expiry (5 minutes) and strict audience validation.
- **Input Sanitization**: Strict Jakarta Validation constraints on all request fields prevent buffer manipulation, SQL/NoSQL injection, and malformed payload attacks.

---

## 5. Observability, Auditing & Monitoring

### Structured JSON Logging & Tracing
Using Logback with Logstash JSON Encoder:
- Every log statement contains structured metadata:
  ```json
  {
    "@timestamp": "2026-10-14T17:25:50.123Z",
    "level": "INFO",
    "logger": "io.softpay.fraud.service.FraudDetectionService",
    "message": "Evaluating transaction: terminalId=TERM-001, threatScore=10, amountEur=120.00, maskedPan=453201******1111",
    "correlationId": "8f2a1b94-d021-4f17-bb12-9c42a2f8914b",
    "terminalId": "TERM-001",
    "fraudScore": 0,
    "status": "ALLOWED"
  }
  ```
- `CorrelationIdFilter` reads or generates `X-Correlation-ID` and binds it to the SLF4J `MDC` context, returning it in the HTTP response header for distributed tracing across services.

### Prometheus & Grafana RED Metrics
Custom metrics registered via Micrometer:
1. **`fraud.evaluations.total` (Counter)**: Tagged with `status={ALLOWED|REJECTED}` to monitor rejection rate anomalies.
2. **`fraud.evaluation.duration` (Timer)**: Tracks execution latency percentiles ($p50, p95, p99, p99.9$). Alerts trigger if $p99 > 15\text{ms}$.
3. **`fraud.rule.triggers.total` (Counter)**: Tagged with `rule={UnknownTerminalRule|AmountVsThreatScoreRule|TerminalVelocityRule|CardVelocityRule}` to identify rule drift and pinpoint specific rule sensitivities.

### Asynchronous Audit Logging via Kafka
Post-evaluation, transaction contexts and rule outputs are published asynchronously to a high-throughput Kafka topic (`fraud-evaluation-audit`):
- Non-blocking: Uses `CompletableFuture` or reactive producer so publishing does not impact evaluation latency.
- Serves as the golden source for compliance auditing, fraud operations investigation, and offline model training.

---

## 6. Framework Choices & Production Roadmap

### Framework & Runtime Rationale
- **Java 21 / 25**:
  - **Virtual Threads (Project Loom)**: Near-infinite concurrency with low overhead for I/O operations.
  - **Records & Pattern Matching**: Ensures immutable value objects, zero boilerplate, and robust domain contracts (`TransactionEvaluationRequest`, `RuleEvaluationResult`).
  - **Sequenced Collections & Vector API**: Facilitates high-performance batch calculations.
- **Spring Boot 3.3**:
  - Native integration with Micrometer 1.13 observation APIs and Springdoc OpenAPI v3.
  - Built-in RFC 7807 `ProblemDetail` support for standardized REST error handling.
  - GraalVM Native Image compatibility for sub-50ms cold starts if serverless scaling is desired.

### Production Roadmap & Future Enhancements
1. **Impossible Travel / Geo-Velocity Rule**:
   - Compare terminal location (GPS coordinates or IP geo-location) against previous transaction coordinates using the Haversine formula:
     $$\text{Speed} = \frac{\text{Distance}(\text{Loc}_1, \text{Loc}_2)}{\Delta t}$$
   - If velocity exceeds realistic travel speeds ($> 800\text{ km/h}$), trigger high-risk fraud flag.
2. **Shadow-Mode Machine Learning Pipeline**:
   - Deploy lightweight Gradient Boosted Decision Tree models (LightGBM/XGBoost) exported to **ONNX format**.
   - Execute in-process via ONNX Runtime Java bindings in parallel with deterministic heuristic rules.
   - Benchmark ML model predictions in shadow mode against live chargeback/TC40 data before enabling live decisioning.
3. **Apache AGE / Neo4j Graph Ring Detection**:
   - Asynchronously map shared card numbers, terminal IDs, device fingerprints, and merchant accounts into a graph store to detect organized syndicates and card-testing botnets in near-real-time.
