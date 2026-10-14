---
name: softpay-backend-developer
description: Expert backend engineer specialized in Edubba's distributed microservices stack featuring Java 25, Spring Boot 4.1.0, Spring Integration, gRPC, Hexagonal Architecture, PostgreSQL (with HQL, pgvector, Apache AGE), Valkey, Vault, Nomad, Flyway, and Mobile Security (Android Keystore / Key Attestation, iOS Keychain / App Attest).
---

# Edubba Backend Developer Skill

This skill guides the design, implementation, refactoring, and maintenance of high-throughput, secure backend microservices within the **Edubba** platform ecosystem.

---

## 1. Core Architecture & Design Philosophy

The Edubba codebase follows **Hexagonal / Ports & Adapters Architecture** integrated with **Spring Integration Messaging Channels** and **gRPC Inbound/Outbound Adapters**:

- **Inbound gRPC Layer (`api.gateway` / grpc services):**
  - Serves as a thin communication adapter.
  - Converts incoming Protobuf messages into Spring `Message<T>` payloads via `MessageBuilder`.
  - Dispatches messages synchronously or asynchronously to `@MessagingGateway` interfaces.
  - Never contains business rules or direct DB access.

- **Messaging Gateway & Channels (`org.springframework.integration`):**
  - `@MessagingGateway` interfaces route requests to designated `DirectChannel` or `ExecutorChannel`.
  - Uses Java 25 Virtual Threads (`AsyncTaskExecutor` with `Executors.newVirtualThreadPerTaskExecutor()`) to prevent blocking thread pools and maximize scalability.

- **Inbound Service Activator (`port.in` & `adapter.in`):**
  - Implements the inbound port interface.
  - Methods annotated with `@ServiceActivator(inputChannel = "...")` and `@Transactional`.
  - Coordinates domain logic, security checks, cryptographic operations, and outbound ports.

- **Domain & Ports (`model`, `port.in`, `port.out`):**
  - Pure business logic, value objects, exceptions extending custom `AbstractRuntimeException`.
  - Output ports (`*DataOut`) represent SPI contracts for database and external services.

- **Persistence & Adapters (`adapter.out`, `persistence`):**
  - Implements output ports using Spring Data JPA, Hibernate, custom SQL/HQL, or graph queries.
  - Uses MapStruct for mapping between JPA entities, domain models, and Protobuf DTOs.

---

## 2. Technology Stack & Guidelines

### Java 25
- Utilize modern Java 25 features: Pattern Matching (`instanceof`, `switch`), Record Patterns, Sealed Classes/Interfaces, Virtual Threads, Sequenced Collections, and Structured Concurrency where appropriate.
- Never block platform threads; prefer virtual threads for I/O operations.

### Spring Boot 4.1.0 & Spring Ecosystem
- **Spring Integration:**
  - Define explicit request channels in `@Configuration` classes (`DirectChannel`, `ExecutorChannel`).
  - Keep `@MessagingGateway` and `@ServiceActivator` signatures aligned with typed Protobuf payloads.
- **Spring Security:**
  - Secure gRPC calls using interceptors (`GlobalGrpcSecurityInterceptor`).
  - Handle MDC context preservation across thread boundaries when dispatching to executor channels.
- **Spring Data JPA / Hibernate:**
  - Prefer immutable entities or proper `@Getter` / `@Setter` with Lombok.
  - Avoid N+1 problems: use `@EntityGraph`, JOIN FETCH in HQL, or projection DTOs.
  - Use `jakarta.persistence.*` annotations exclusively.

### Databases & Querying
- **PostgreSQL & Flyway:**
  - Database schema changes MUST be managed through Flyway migration scripts (`V<Version>__<Description>.sql`).
  - Write optimized SQL and HQL queries.
- **Vector Database (pgvector):**
  - Store vector embeddings for semantic search, similarity matching, and AI recommendations.
  - Use indexing (`HNSW` / `IVFFlat`) with appropriate distance metrics (Cosine, Euclidean, Inner Product).
- **Apache AGE (Graph Database on PostgreSQL):**
  - Model social graphs, relationships, knowledge graphs, and complex dependencies using OpenCypher queries on Apache AGE.

### Caching & In-Memory Storage (Valkey)
- Utilize **Valkey** (via Valkey Glide or Redis-compatible client) for distributed caching, session storage, distributed rate limiting, and ephemeral challenge/nonce verification.
- Enforce strict TTLs for security tokens, nonces, and challenge requests.

### HashiCorp Vault & Nomad
- **HashiCorp Vault:** Secure secret management, dynamic database credentials, encryption-as-a-service (Transit secret engine), and PKI certificate authority.
- **Nomad:** Workload orchestration, service job specifications, task drivers, resource allocation, and zero-downtime rolling updates.

### gRPC & Protocol Buffers (Protobuf)
- Define clean contracts in `.proto` files (`proto3` syntax) with options:
  `option java_multiple_files = true;`
  `option java_package = "com.edubba.<module>.proto";`
- Use `io.grpc:grpc-netty-shaded` for Netty dependencies.
- Handle gRPC error mapping gracefully using custom interceptors (`GlobalGrpcExceptionHandler`).

### Mobile Security & Hardware Attestation

#### Android Security (Keystore & Key Attestation)
- **Key Attestation Verification:**
  - Parse and validate Android X.509 certificate chains using Google Attestation Root CA (`AndroidKeyAttestationComponent`).
  - Inspect ASN.1 Attestation Extension (`1.3.6.1.4.1.11129.2.1.17`).
  - Validate attestation challenge (nonce) to defend against replay attacks.
  - Check `attestationSecurityLevel` (StrongBox or TEE), `keymasterVersion` / `keyMintVersion` (>= 4 for Key Import).
  - Verify package name, digest, and signing certificate hashes (`AttestationPackageInfo`).
- **Key Wrapping & Cryptography:**
  - Envelope encryption: Generate AES-256-GCM Data Encryption Key (DEK) on the server.
  - Encrypt/Wrap DEK using device's attested Key Encryption Key (KEK / Public Key) via RSA-OAEP / ECDH.
  - Wipe in-memory raw byte arrays immediately after use (`cryptographyComponent.clearBytes(...)`).

#### iOS Security (Keychain & App Attest)
- **DeviceCheck / App Attest:**
  - Verify Apple App Attest CBOR/X.509 attestation object and clientDataHash (SHA-256 of challenge nonce).
  - Validate receipt, counter integrity, and AAGUID.
  - Store validated public keys tied to device/app instance in secure persistence.

---

## 3. Code Conventions & Standards

1. **Lombok Usage:**
   - Use `@RequiredArgsConstructor` for constructor dependency injection.
   - Use `@Slf4j` for logging.
   - Use `@Getter`, `@Setter`, `@Builder`, and `@ToString(exclude = {...})` to prevent leaking sensitive credentials/keys in logs.

2. **Error Handling & Exceptions:**
   - Extend `AbstractRuntimeException` for domain exceptions.
   - Map domain exceptions to appropriate `io.grpc.StatusRuntimeException` or HTTP status codes.
   - Never expose internal stack traces or cryptographic keys in client error messages.

3. **Transactions:**
   - Apply `@Transactional(propagation = Propagation.REQUIRED)` on `@ServiceActivator` or service methods modifying data state.

4. **Testing:**
   - Unit tests with JUnit 5, Mockito, and Spring Integration Test.
   - Test key attestation verification, replay protection, and cryptographic wrapping rigorously.
