# Inventory Tracking System

A small business needs to know what stock it has and how that has changed over time. Deliverable: a complete, reliable, tested app that runs locally with Docker, includes README instructions, and ships seed data so reviewers can test without creating records manually.

**Stack:** Java 25, Spring Boot 4.1.1 (Maven), PostgreSQL, React (Vite + TypeScript). Package: `dev.hpsoftware.torch`.
**Constraints:** 3-day delivery; no unnecessary infrastructure (no queues, caches, microservices, state libs); no authentication.

## Core design decision
**Append-only stock-movement ledger.** Every stock change is a `StockMovement` row (signed delta, type, balance_after, timestamp); history is never edited. `product.quantity_on_hand` is a denormalised current balance, updated in the same transaction under a pessimistic row lock (`SELECT … FOR UPDATE`) so concurrent movements can't oversell or lose updates. Negative stock is rejected (409).

**Invariant:** `quantity_on_hand` == sum of all of that product's `quantity_delta`s, always >= 0.

## Data model
- `product`: id, sku (unique), name, description, unit, reorder_level, quantity_on_hand (CHECK >= 0, **always starts at 0**; create-product cannot set stock — initial inventory is a RECEIPT movement), active, created_at, version
- `stock_movement`: id, product_id FK (indexed with created_at), type (`RECEIPT`, `ISSUE`, `ADJUSTMENT`), quantity_delta (non-zero, signed), balance_after, note, created_at
- Flyway holds **schema versions only** (`src/main/resources/db/migration`, `V1__schema.sql`, then `V2__...`, etc.). No data migrations.
- Hibernate `ddl-auto: validate`; `open-in-view: false`; DTO records only, never expose entities.

## Movement rules
- RECEIPT: positive quantity. ISSUE: positive requested quantity, service converts to a negative delta. ADJUSTMENT: nonzero signed delta (no target-count adjustments in v1).
- Rejected: zero quantity, wrong sign, result below zero (409 insufficient stock).

## Seed data
`SeedDataLoader` (`ApplicationRunner`, `@Profile("seed")`), not Flyway. Uses the **same inventory service** as normal operations (create product, then record movements). Idempotent (skips if products exist). ≈12 products with ~60 days of back-dated movements; a few below reorder level, one out of stock. Compose sets `SPRING_PROFILES_ACTIVE=seed`.

## REST API
- `GET/POST /api/products`, `GET/PUT /api/products/{id}`, `DELETE` = soft-deactivate. `?q=` search (name or SKU, partial), `?sku=` exact SKU lookup (list of 0 or 1, never 404), `?lowStock=true`.
- `POST /api/products/{id}/movements` `{type, quantity, note}`
- `GET /api/products/{id}/movements?page=&size=` (newest first)
- Optional: `GET /api/movements?from=&to=&type=`, `GET /api/products/{id}/balance-history?days=`; P1: `GET /api/summary`
- One `GlobalExceptionHandler` (`@RestControllerAdvice`, package `exception`) turns every exception into an RFC 7807 `ProblemDetail` (400 validation, 404, 409 duplicate SKU / insufficient stock). Controllers and services never build error responses; each endpoint slice adds the handler(s) it needs. Validation (400) returns "Validation failed" plus a per-field `errors` map. Inject a `Clock` bean for testable timestamps.
- OpenAPI docs via springdoc (`/swagger-ui.html`).

## Dependencies (exactly)
webmvc, data-jpa, validation, flyway (+ `flyway-database-postgresql`), postgresql, actuator (health), springdoc-openapi (Boot 4-compatible 3.x — verify), tests: spring-boot-starter-test (+ webmvc-test). Verify Boot 4 starter names and versions resolve before writing app code. No Testcontainers initially.

## Frontend (`frontend/`)
Vite + React + TypeScript, plain `fetch` wrapper, React Router. Build only after the core API works.
- Products list (search, low-stock badge, create form)
- Product detail: current stock, receive/issue/adjust form showing server errors, paged movement history
- Shared loading/error/empty handling via a small `useApi` hook
- Optional: dashboard summary, balance-over-time SVG chart, global activity feed

## Docker / delivery
- `docker-compose.yml`: `db` (postgres:17-alpine, healthcheck, volume), `backend` (multi-stage maven temurin-25 → temurin-25 jre), `frontend` (node build → nginx, proxies `/api` to backend; no CORS needed). Env-driven DB config (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- UI http://localhost:3000, API http://localhost:8080.
- README: prerequisites, run, reset (`docker compose down -v`), run tests, API overview, design decisions/trade-offs (ledger, locking, soft delete), out-of-scope list, how it would evolve.

## Testing
- Unit: movement service rules with Mockito/`Clock`.
- Integration (`@SpringBootTest`, compose Postgres, separate `torch_test` DB, real Flyway): API happy paths and error mapping via MockMvc; seed sanity (ledger sum == quantity_on_hand); **concurrency test** — N parallel issues against stock of N-k, exactly k fail, balance never negative.
- Frontend: Vitest + React Testing Library (movement form errors, product list search/low-stock badge).

## Priorities
**P0 (in order):**
0. Verify Boot 4.1.1 / Java 25 / springdoc / dependency versions build.
1. Vertical slice: V1 schema, product creation (stock 0), movement service, transactional consistency, minimal REST + errors, unit + integration tests.
2. Concurrency strategy (pessimistic lock) + concurrency integration test, once the basic movement workflow works.
3. Idempotent seed loader.
4. Backend Dockerfile + `backend` service in compose (before the frontend, so the API can be run and verified in a container).
5. Frontend + its tests, then frontend Dockerfile, full compose, README.
6. **Acceptance:** `docker compose up --build` starts with seeded data; a reviewer can create products, receive stock, issue stock, get an invalid issue rejected, and inspect movement history.

**P1:** dashboard summary, low-stock filter.
**Optional (only after all P0):** balance chart, global activity feed, GitHub Actions, Testcontainers.
**Not doing:** authentication.

## Working method
Build top-down, one endpoint at a time: controller -> service -> repository -> exceptions, with tests, before the next endpoint. Implement one small, testable step at a time; run the relevant tests and report actual results before proceeding. No optional feature while any P0 is incomplete.

## Assumptions
Single warehouse/location, integer quantities, soft delete only.

## Verify the complete application
The primary acceptance criterion is that `docker compose up --build` starts the application with usable sample data.

A reviewer must be able to:
1. View and search products.
2. Create and update a product.
3. Receive stock.
4. Issue stock.
5. Attempt an invalid issue and receive a clear error.
6. Verify that rejected operations leave stock unchanged.
7. Inspect movement history.
8. Restart the application without losing persisted data.

Run the relevant automated tests and verify the final application workflow.

## Plan vs. current state
The sections above are the original plan and stay as written. This section records where the project actually stands and where it differs from the plan. `ROADMAP.md` has the step-by-step detail.

| Area | Planned | Current state |
|---|---|---|
| P0 items 0-6 | Backend, concurrency, seed, backend Docker, frontend, full compose, README, acceptance | All done. `make up` starts the seeded app; the acceptance items are ticked in `ROADMAP.md` (P0.6). |
| P1 | Dashboard summary, low-stock filter | Low-stock filter done. `GET /api/summary` not done. |
| Optional | Balance chart, activity feed, GitHub Actions, Testcontainers | GitHub Actions done (tests, Compose smoke test, publish, deploy). The rest not done. Also not done: `GET /api/movements`, `GET /api/products/{id}/balance-history`. |
| ADJUSTMENT movements | Nonzero signed delta, rejected if below zero | Not implemented: the service treats it like a receipt. Deferred in `ROADMAP.md`, listed in the README's Known gaps. The UI form offers only receive and issue. |
| Tests | Integration tests as part of the normal run | Split: `./mvnw test` runs the 56 unit tests with no database (what CI runs); `./mvnw verify` adds the 23 `*IT` integration tests, which need PostgreSQL. Plus 10 frontend tests (Vitest). |
| Commands | `docker compose up --build`, raw `./mvnw` commands | A grouped `Makefile` (`make help` lists every target): `make up`, `make run`, `make run/seed`, `make test/unit`, `make test/all`, `make test/frontend`, `make reset`. The README uses these targets. |
| Frontend | List, search, low-stock badge, create form, detail, movement form, paged history | Done, plus an edit-product form. History shows only the latest 20 movements (no paging); no deactivate button. |
| Exception handling | One `GlobalExceptionHandler` for 400/404/409 | Done, but malformed JSON, unknown URLs and wrong methods still return 500 (Known gaps). |
