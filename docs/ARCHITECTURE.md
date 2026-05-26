# Architecture

This document captures the design decisions made before writing any production
code. It exists so the evaluator can understand *why* the code looks the way it
does, not just *what* it does.

---

## 1. Problem statement

Expose a single REST endpoint that, given a product id, returns the **full
detail** of its similar products:

```
GET /product/{productId}/similar  ->  200 [ProductDetail, ...] | 404
```

Two upstream APIs are already provided (mocked at `localhost:3001`):

- `GET /product/{id}/similarids` — returns an array of similar product ids.
- `GET /product/{id}` — returns the detail of a single product.

The service to build (`yourApp`, port `5000`) is the orchestrator in between.

---

## 2. What the test actually measures

The provided k6 script runs **5 scenarios with 200 VUs each**, including:

| Scenario   | Trap                                                        |
|------------|-------------------------------------------------------------|
| `normal`   | Happy path                                                  |
| `slow`     | One similar takes 1 s, another takes 5 s                    |
| `verySlow` | One similar takes **50 s**                                  |
| `notFound` | One similar returns **404**                                 |
| `error`    | One similar returns **500**                                 |

The official evaluation criteria are: **code clarity & maintainability**,
**performance**, and **resilience**. Translated to engineering:

1. **Fan-out must be concurrent**, never sequential.
2. **Individual upstream failures (404/500) must NOT poison the response.**
3. **Slow upstreams must NOT block threads or exhaust the connection pool.**
4. **Repeated ids across scenarios reward request coalescing + a short cache.**

---

## 3. Stack and rationale

| Concern              | Choice                                            | Why                                                                 |
|----------------------|---------------------------------------------------|---------------------------------------------------------------------|
| Language / runtime   | Java 21 (Temurin)                                 | LTS; `record`s express immutable domain types without Lombok.       |
| Framework            | Spring Boot **3.5.14**                            | Latest patch of the mature 3.5 line; ecosystem fully verified.      |
| Web stack            | Spring **WebFlux** only                           | Non-blocking I/O is a natural fit for high-fan-out aggregation. `spring-web` is **explicitly excluded** — mixing both is a known anti-pattern. |
| HTTP client          | `WebClient`                                       | Reactive, non-blocking, integrates with Reactor backpressure.       |
| Resilience           | **Resilience4j-reactor** (CircuitBreaker + TimeLimiter) | Industry standard; per-call time limit + per-dependency circuit. |
| Caching + coalescing | **Caffeine `AsyncCache`**                         | Built-in request coalescing (singleflight): N concurrent callers for the same key share one upstream call. No custom cache. |
| Config externalization | `@ConfigurationProperties` over `record`         | Type-safe, immutable, validated at startup, tunable without recompile. |
| Error handling       | `@RestControllerAdvice` (global)                  | One place maps domain/infra exceptions to HTTP status codes.        |
| Money type           | `java.math.BigDecimal` for `price`                | Financial precision; `double` is wrong for currency.                |
| Tests                | JUnit 5 + WireMock + StepVerifier + WebTestClient | Realistic upstream simulation + reactive assertions.                |
| Observability        | Micrometer + `/actuator/prometheus`               | Plugs into the provided Grafana/InfluxDB stack.                     |
| Build                | Maven (via Maven Wrapper)                         | Single source of truth, no global install required.                 |
| Models               | Java `record`s — no Lombok, no MapStruct          | Records cover equals/hashCode/toString; static mappers are trivial. |

Why **WebFlux over MVC + virtual threads** for this specific test: the workload
is *latency-bound I/O fan-out* (a few outbound calls per request, some very
slow). Reactor's `flatMap(concurrency)` and per-call `timeout()` express the
required behavior idiomatically and have first-class support in
Resilience4j-reactor. Virtual threads would also work, but the reactive style
makes the timeout/cancellation semantics obvious in the code.

---

## 4. Architectural style: Hexagonal (Ports & Adapters)

The codebase follows the canonical Hexagonal layout. The hexagon has two
sides: what's **inside** (the `application` package) and what's **outside**
(the `adapter` package). Dependencies always point **inward**, toward the
application.

```
                 ┌──────────────────────────────────────────────┐
                 │                   Adapters                   │
                 │  (Spring, Netty, WebClient, Caffeine, R4j)   │
                 │  ┌────────────────────────────────────────┐  │
                 │  │                Application             │  │
                 │  │  ┌──────────────────────────────────┐  │  │
                 │  │  │              Domain              │  │  │
                 │  │  │  Product (record)                │  │  │
                 │  │  │  ProductNotFoundException        │  │  │
                 │  │  └──────────────────────────────────┘  │  │
                 │  │  Ports (in/out interfaces)             │  │
                 │  │  Services (use case implementations)   │  │
                 │  └────────────────────────────────────────┘  │
                 │                                              │
                 │  Inbound adapters:  ProductController, …     │
                 │  Outbound adapters: ProductHttpAdapter, …    │
                 └──────────────────────────────────────────────┘
```

- **`application.domain.model`**: pure types. Zero framework imports.
- **`application.domain.service`**: use-case implementations
  (package-private). Talk only to ports.
- **`application.port.in`**: use-case interfaces — the driver side of the
  hexagon.
- **`application.port.out`**: outbound interfaces (one per operation, ISP
  applied) — the driven side of the hexagon.
- **`adapter.in.web`**: Spring controllers, error handlers, response DTOs.
- **`adapter.out.http`**: WebClient implementation of the outbound ports,
  cache, resilience, configuration.

Benefits for this test: the use-case logic (fan-out, filter failures, preserve
order) is trivially unit-testable with mocked port interfaces, no HTTP layer
involved.

---

## 5. Folder structure

```
src/main/java/dev/joseignacio/similar/
├── SimilarProductsApplication.java
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── ProductController.java
│   │       ├── GlobalErrorHandler.java          # @RestControllerAdvice
│   │       └── dto/ProductResponse.java         # record
│   └── out/
│       └── http/
│           ├── ProductHttpAdapter.java          # implements both out ports
│           ├── WebClientConfig.java
│           ├── ResilienceConfig.java            # CircuitBreaker + TimeLimiter beans
│           ├── CacheConfig.java                 # Caffeine AsyncCache bean
│           ├── ExternalApiProperties.java       # @ConfigurationProperties record
│           └── dto/ProductDetailResponse.java   # record (upstream payload)
└── application/
    ├── domain/
    │   ├── model/Product.java                   # record (id, name, BigDecimal price, boolean availability)
    │   ├── exception/ProductNotFoundException.java
    │   └── service/
    │       └── GetSimilarProductsService.java   # implements use case, package-private
    └── port/
        ├── in/GetSimilarProductsUseCase.java    # driver port
        └── out/
            ├── FindSimilarIdsPort.java          # driven port (one method)
            └── LoadProductPort.java             # driven port (one method)
```

**Outbound ports are split per operation (ISP)**: each port has exactly one
method. A single `ProductHttpAdapter` implements both. This keeps each
interface focused and lets a future caller depend only on what it needs.

---

## 6. Key design decisions

### 6.1 Concurrent fan-out (parallel + ordered)
The use case receives `Flux<String>` of ids and resolves details with
`flatMapSequential(loader, CONCURRENCY)`. The `Sequential` variant is the
key choice: lookups run **in parallel** (capped at `CONCURRENCY`) but the
emission order matches the upstream id order — required by the contract
(*"List of similar products to a given one ordered by similarity"*). Plain
`flatMap` would parallelise but lose order; `concatMap` would preserve order
but serialise the calls.

### 6.2 Partial-failure tolerance — per-item, not global
Each per-id lookup is wrapped with `onErrorResume(e -> Mono.empty())`
**inside** the `flatMap` lambda, so the error is evaluated in the scope of
that one item. A 404 or 500 from a single similar product yields a missing
entry in the output list, **never a failure of the whole request**. The 404
of the *base* product (`findSimilarIds`) does propagate as a 404 to the
client.

> **Anti-pattern explicitly avoided**: `Flux.onErrorContinue` at the outer
> stream level. Project Reactor documents it as brittle — it operates on the
> upstream and can suppress errors in unexpected places when more operators
> are added later. The per-item `onErrorResume` is the recommended pattern.

### 6.3 Per-call time limit
A `TimeLimiter` (from Resilience4j) is applied per upstream call (~2 s). The
50-second mock is bounded, so one slow similar cannot block the whole
response. Reactor cancellation propagates and frees the connection. Timeouts
are **per call**, never multi-second global timeouts that defeat the point.

### 6.4 Circuit breaker per upstream
A single `CircuitBreaker` instance guards the upstream `localhost:3001`.
Sliding window count-based (size 10, min 5 calls), `failureRateThreshold=50%`,
`slowCallRateThreshold=80%` with `slowCallDurationThreshold=2s`,
`waitDurationInOpenState=30s`, automatic transition to half-open with 3 probes.
Protects both us and the upstream from cascading failure.

`ProductNotFoundException` is excluded from failure counting via
`ignore-exceptions` — a `404` is a deterministic, legitimate upstream answer,
not infrastructure distress, and should not contribute to opening the circuit.

### 6.5 Cache with built-in request coalescing
The `loadProduct` lookup is wrapped with a **Caffeine `AsyncCache`** (TTL
~30 s, bounded `maximumSize`). Two effects in one component:

1. **Cache**: repeated ids across scenarios (1, 2, 3 appear in multiple k6
   scenarios) are served from memory without hitting upstream.
2. **Request coalescing (singleflight)**: when N concurrent callers ask for
   the same missing key, Caffeine fires **one** loader and shares the result
   with all N subscribers. Critical for the `verySlow` scenario — 200 VUs
   asking for `/product/10000` produce **one** 50-second call (then bounded
   by the time limiter), not 200.

This is implemented with Caffeine out of the box; no custom
`ConcurrentHashMap<String, Mono>` plumbing.

### 6.6 Centralized error handling
A single `@RestControllerAdvice` (`GlobalErrorHandler`) maps:

| Exception                                  | HTTP status                |
|--------------------------------------------|----------------------------|
| `ProductNotFoundException` (base 404)      | `404 Not Found`            |
| `CallNotPermittedException` (circuit open) | `503 Service Unavailable`  |
| `TimeoutException`                         | `504 Gateway Timeout`      |
| anything else                              | `500 Internal Server Error` (logged) |

Controllers stay clean of try/catch and status mapping.

### 6.7 Externalized configuration via `@ConfigurationProperties`
All tunable values live in `application.yml` and are bound to an immutable
record:

```java
@ConfigurationProperties(prefix = "external-api")
public record ExternalApiProperties(String baseUrl, Duration cacheTtl, long cacheMaxSize) {}
```

Bound at startup, immutable thereafter. No `@Value` scattered across the
codebase. Per-call timeout lives in Resilience4j's own properties
(`resilience4j.timelimiter.instances.productDetails.timeout-duration`),
tunable alongside the rest of the resilience knobs.

### 6.8 No DTO leak across layers
Three distinct types cross the boundaries:

1. `ProductDetailResponse` — record bound to the upstream JSON payload.
2. `Product` — domain record, immutable, framework-free.
3. `ProductResponse` — record returned by the controller to the client.

Translation happens at the edges; the domain never crosses the wire.

### 6.9 What we deliberately did NOT do
Decisions taken against, with reasons — useful for the evaluator to see the
trade-offs were considered.

- **No retry on timeout/5xx.** Retrying a 50-second call makes p99 worse, not
  better. Circuit breaker + cache handle the same failure modes more
  cheaply.
- **No scheduled cache warming with hardcoded seed ids.** Pre-fetching only
  the ids the k6 test happens to exercise would optimize the benchmark, not
  the system. The cache is filled by organic traffic; coalescing and TTL do
  the rest.
- **No MapStruct.** With records and identical field names, a four-line
  static mapper is clearer than annotation-processor magic.
- **No Lombok.** Records cover the constructors, accessors, equals,
  hashCode, and toString that Lombok used to generate.
- **No mixed `spring-web` + `spring-webflux`.** WebFlux only. Both on the
  classpath confuses auto-configuration and pulls in sync classes that are
  never used.
- **No fat repository interface.** Outbound ports are split per operation
  (`FindSimilarIdsPort`, `LoadProductPort`). One `ProductHttpAdapter`
  implements both. This is Interface Segregation applied to the hexagon.

---

## 7. Testing strategy (TDD)

| Layer                       | Test type             | Tooling                           |
|-----------------------------|-----------------------|-----------------------------------|
| Domain (records)            | None (no behavior)    | —                                 |
| Application service         | Unit (mocked ports)   | JUnit 5 + Mockito + StepVerifier (`withVirtualTime` for the parallelism check) |
| Outbound HTTP adapter       | Integration           | WireMock — 200, 404, 500, cache reuse, concurrent coalescing |
| Inbound web adapter         | Slice                 | `@WebFluxTest` + `WebTestClient` — full error mapping (404, 503, 504, 500) |
| Full app                    | E2E load test         | The provided k6 + Grafana         |

Tests are written **before** the implementation for each layer
(Red → Green → Refactor).

---

## 8. Branching & commit conventions

- **GitHub Flow**: `main` is always green and deployable.
- Work happens on short-lived `feature/*`, `fix/*`, `chore/*`, `refactor/*`,
  `docs/*` branches, merged via PR (or fast-forward for solo work).
- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`, `test:`,
  `refactor:`). One-line messages preferred; body only when it adds context
  beyond the diff.
- Each PR keeps a single concern (scaffold, deps, one layer, etc.) to keep
  reviews tight.

---

## 9. How to run

```bash
# Start the mocks, InfluxDB and Grafana
docker compose up -d simulado influxdb grafana

# Run the application
./mvnw spring-boot:run

# Smoke test
curl http://localhost:5000/product/1/similar

# Load test
docker compose run --rm k6 run scripts/test.js

# Dashboard
open http://localhost:3000/d/Le2Ku9NMk/k6-performance-test
```

## 10. How to test

```bash
./mvnw test           # unit + slice tests
./mvnw verify         # adds integration tests (WireMock)
```
