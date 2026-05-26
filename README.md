# Similar Products Service

> Spring Boot 3.5 · Java 21 · WebFlux · Hexagonal Architecture
> Resilience4j (CircuitBreaker + TimeLimiter) · Caffeine AsyncCache (with request coalescing)

A reactive REST service that, given a product id, returns the **full detail
of its similar products** — aggregating two upstream mocked APIs into a
single, resilient endpoint.

This repository is the candidate solution for the AMS Solutions / Inditex
backend technical test. See [`docs/CHALLENGE.md`](docs/CHALLENGE.md) for the
original problem statement and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for the full design rationale.

---

## Problem in one paragraph

The service receives `GET /product/{productId}/similar` and returns the full
detail of every similar product. Two upstream APIs already exist (mocked at
`localhost:3001`): one returns the list of similar ids, the other returns
one product's detail. The upstream is intentionally cruel — some products
respond in **50 seconds**, others return `404`/`500`. The evaluation
criteria are **code clarity**, **performance**, and **resilience**.

---

## Architecture at a glance

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

Canonical Hexagonal layout. Dependencies always point **inward**. The domain knows nothing about
HTTP, Spring, or Caffeine.

Full breakdown in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Key design decisions

1. **Concurrent fan-out with preserved order.** The use case resolves
   similar product details with Reactor's `flatMapSequential(loader, 10)` —
   parallel execution capped at 10 in-flight calls, **ordered emission** so
   the client receives the products in the upstream similarity order.

2. **Per-item failure isolation.** Each per-id lookup is wrapped with
   `onErrorResume(e -> Mono.empty())` *inside* the `flatMap` lambda. A 404
   or 500 from a single similar product yields a missing entry — never a
   failure of the whole request. `Flux.onErrorContinue` is **explicitly
   avoided** (Reactor team documents it as brittle).

3. **Per-call time limit.** Resilience4j's `TimeLimiter` caps every upstream
   call at **2 seconds**. The provided 50-second mock cannot block a thread
   or stall the response. Reactor cancellation propagates and frees the
   connection.

4. **Circuit breaker per upstream.** A single `CircuitBreaker` guards the
   product API. Sliding window count-based, opens at 50% failure rate or 80%
   slow-call rate. `ProductNotFoundException` is excluded from failure
   counting — a 404 is a legitimate upstream answer, not infrastructure
   distress.

5. **Cache with built-in request coalescing.** A Caffeine **`AsyncCache`**
   (TTL 30s, bounded `maximumSize`) serves repeated ids and — crucially —
   coalesces concurrent gets: when 200 callers request the same missing key,
   exactly **one** upstream call fires; all 200 subscribe to the same
   future.

6. **Centralised error handling.** A single `@RestControllerAdvice` maps
   `ProductNotFoundException → 404`, `CallNotPermittedException → 503`,
   `TimeoutException → 504`, and any other exception → `500` (logged).
   Controllers stay clean of try/catch.

---

## Load-test results (k6 — 5 scenarios, 200 VUs each)

Run with the bundled `docker compose run --rm k6 run scripts/test.js`
against the live application. Results:

| Metric                 | Value           |
|------------------------|-----------------|
| Total HTTP requests    | **16 682**      |
| Throughput             | **268 req/s**   |
| Median latency         | **15 ms**       |
| p90 latency            | 78 ms           |
| p95 latency            | 492 ms          |
| **Max latency**        | **2.16 s** (TimeLimiter cap working as designed) |
| Application crashes    | **0**           |

Without the resilience layer the max latency would be ~50 s. Without
coalescing, the 200-VU `verySlow` scenario would generate 200× concurrent
upstream calls to the slow product; with it, the upstream sees one.

The Grafana dashboard is provisioned automatically at
`http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`.

---

## Getting started

### Prerequisites

- Java 21 (Temurin recommended)
- Docker & Docker Compose
- macOS users on Apple Silicon: disable **AirPlay Receiver** in System
  Settings → General → AirDrop & Handoff (it occupies port 5000).

### 1. Start the upstream mocks + observability stack

```bash
docker compose up -d simulado influxdb grafana
```

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The service listens on `http://localhost:5000`.

### 3. Smoke test

```bash
curl http://localhost:5000/product/1/similar | jq .
```

### 4. Load test

```bash
docker compose run --rm k6 run scripts/test.js
```

Open the Grafana dashboard while it runs:
<http://localhost:3000/d/Le2Ku9NMk/k6-performance-test>

---

## Testing

```bash
./mvnw test
```

| Suite                            | Style                                      | Tests |
|----------------------------------|--------------------------------------------|-------|
| `GetSimilarProductsServiceTest`  | Unit (mocked ports + `StepVerifier.withVirtualTime` for parallelism) | 6 |
| `ProductHttpAdapterTest`         | Integration with WireMock (200, 404, 500, coalescing, cache reuse)  | 8 |
| `ProductControllerTest`          | Slice (`@WebFluxTest` + `WebTestClient`, includes error handler)    | 6 |
| `SimilarProductsApplicationTests`| Context-loads smoke test                                            | 1 |
| **Total**                        |                                            | **21** |

---

## API endpoints

| Endpoint                              | Description                                              |
|---------------------------------------|----------------------------------------------------------|
| `GET /product/{productId}/similar`    | The contract endpoint — returns `[ProductResponse, …]`   |
| `GET /actuator/health`                | Liveness / readiness                                     |
| `GET /actuator/metrics`               | Application metrics                                      |
| `GET /actuator/prometheus`            | Prometheus scrape endpoint                               |
| `GET /actuator/circuitbreakers`       | Real-time circuit breaker state (state, failure rate, …) |

---

## Tech stack

| Concern        | Choice                                                  |
|----------------|---------------------------------------------------------|
| Language       | Java 21 (Temurin)                                       |
| Framework      | Spring Boot **3.5.14**                                  |
| Web stack      | Spring **WebFlux** only (no `spring-web` mixed in)      |
| HTTP client    | `WebClient`                                             |
| Resilience     | **Resilience4j 2.4.0** (`-reactor` + `-spring-boot3`)   |
| Caching        | **Caffeine 3.x** `AsyncCache`                           |
| Tests          | JUnit 5 · WireMock 3.13 · StepVerifier · WebTestClient  |
| Observability  | Micrometer + `/actuator/prometheus` → Grafana           |
| Build          | Maven via Maven Wrapper                                 |
| Models         | Java `record`s — no Lombok, no MapStruct                |

---

## Branching & commit conventions

- **GitHub Flow on `main`**: short-lived branches (`feature/*`, `fix/*`,
  `chore/*`, `refactor/*`, `docs/*`) merged via `git merge --ff-only` for a
  linear history.
- **Conventional Commits** with one-line subjects. Body only when it adds
  context beyond the diff.

---

## What this project deliberately does NOT do

Each of these was an explicit choice with a reason. They appear in the
commit log only by their absence.

- **No Lombok.** Java 21 `record`s cover the constructors, accessors,
  `equals`, `hashCode`, and `toString` Lombok used to generate.
- **No MapStruct.** Field-for-field mappings between records with identical
  names are clearer as a four-line static factory than as annotation-processor
  output.
- **No mixed `spring-web` + `spring-webflux`.** WebFlux only. Both on the
  classpath confuses auto-configuration and pulls in sync classes that are
  never used.
- **No scheduled cache warming with hardcoded seed ids.** Pre-fetching the
  exact ids the k6 test exercises would optimise the benchmark, not the
  system. The cache is filled by organic traffic; coalescing + TTL handle
  the rest.
- **No retry on timeouts or 5xx.** Retrying a 50-second call makes p99
  worse, not better. The circuit breaker and cache handle the same failure
  modes more cheaply.
- **No `Flux.onErrorContinue`.** Documented by the Reactor team as brittle
  — it can suppress errors in unexpected places as operators are added.
  Per-item `onErrorResume` is used instead.
- **No custom `ConcurrentHashMap<String, Mono>` cache.** Caffeine's
  `AsyncCache` provides bounded eviction, TTL, and request coalescing out
  of the box. Reimplementing it is unnecessary complexity.
- **No modification of the provided `docker-compose.yaml`.** It is the
  evaluator's test infrastructure; changing it would be off-script.

---

## Repository layout

```
.
├── README.md                          ← you are here
├── docs/
│   ├── ARCHITECTURE.md                ← full design rationale
│   └── CHALLENGE.md                   ← original problem statement
├── pom.xml
├── src/
│   ├── main/java/dev/joseignacio/similar/
│   │   ├── adapter/{in/web, out/http}
│   │   └── application/{domain, port}
│   ├── main/resources/application.yml
│   └── test/java/…                    ← mirror of main, 21 tests
├── docker-compose.yaml                ← provided: simulado + grafana + influxdb + k6
└── shared/                            ← provided: k6 scripts, grafana dashboards, mocks
```
