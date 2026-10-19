# Torch — Inventory Tracking System

Torch is a small-business inventory tracking application. It is designed to answer two questions:

1. **What stock do we have right now?**
2. **How has that stock changed over time?**

The core design is an append-only stock-movement ledger. Every inventory change is recorded, and the product's current balance is maintained alongside that history.

> **Project status — 2026-10-18:** The Spring Boot backend for product and stock-movement APIs is implemented. The current README reports 79 passing tests: 56 unit tests (`./mvnw test`, no database needed) and 23 integration tests (`./mvnw verify`, needs PostgreSQL) that cover the create/receive/issue/history workflow, rejected issues, repository queries, a concurrency test, and the seed data. The seed loader (profile `seed`) is implemented and tested; the React frontend is in progress (scaffold, API client, `useApi` hook, product list with search and low-stock badges, product detail page, the receive/issue form and the latest-20 movement history are implemented; 9 Vitest tests pass with `npm test` in `frontend/`; create product and the frontend Docker service are still to do) and the full Docker Compose application is still to be finished.

## Contents

- [Technology stack](#technology-stack)
- [Current functionality](#current-functionality)
- [Run the current backend locally](#run-the-current-backend-locally)
- [API overview](#api-overview)
- [Data model](#data-model)
- [Technical decisions and trade-offs](#technical-decisions-and-trade-offs)
- [Run the tests](#3-run-the-tests)
- [Intentionally excluded functionality](#intentionally-excluded-functionality)
- [Known gaps](#known-gaps)
- [Planned full-application setup](#planned-full-application-setup)
- [What I would improve with another week](#what-i-would-improve-with-another-week)
- [Time spent](#time-spent)
- [Use of AI tools](#use-of-ai-tools)

## Technology stack

### Backend

- Java 25
- Spring Boot 4.1.1
- Maven as Package Management
- PostgreSQL 17
- Flyway database migrations
- springdoc-openapi for API documentation
- Spring Boot Actuator for health information

### Frontend — in progress

- React
- TypeScript
- Vite
- React Router (`react-router` v7)
- Tailwind CSS
- Vitest + React Testing Library

### Local development and delivery

- Docker Compose for PostgreSQL and, in the planned final setup, the application services
- Environment-based database configuration
- A README-driven setup intended to work from a clean checkout on macOS

### Why this stack?

The assessment permits choosing a framework and database. Java and Spring Boot were selected because I have used them in previous projects and can focus on the domain rules rather than learning a new backend framework during the take-home window.

React with Vite was selected over Next.js because this is an administrative interface backed by a separate Spring Boot REST API. The application does not require server-side rendering or search-engine optimization, and Vite keeps the frontend build and development setup small. React Router provides client-side navigation.

Maven was selected over Gradle because either tool would meet the requirements, and Maven's conventional lifecycle is sufficient for this application's build and tests. The Maven Wrapper makes the build reproducible without requiring a globally installed Maven version.

## Current functionality

The current backend includes:

- Product creation, retrieval, listing, update, and soft deactivation.
- SKU uniqueness validation.
- Product search and low-stock filtering.
- Recording stock receipts and issues.
- Paged per-product movement history.
- Transactional stock updates and a pessimistic product-row lock for stock movements.
- Validation and consistent API error responses using RFC 7807 `ProblemDetail`.
- Flyway-managed database schema.
- DTO records rather than exposing JPA entities directly.
- An injected `Clock` to make timestamp behavior testable.
- An idempotent seed loader (profile `seed`) with 12 products and about 60 days of back-dated movements, created through the normal services.

### Current limitations

The following are not yet complete in the current repository state:

- React frontend: the product list, product detail, receive/issue form and latest-20 movement history are implemented; create product and the frontend Docker service are still to do. Edit product in the UI is planned as a known gap.
- Full `docker compose up --build` application delivery: the `frontend` service is still missing.
- Dashboard summary and historical balance chart.
- Some input-validation and exception-handling hardening described in [Known gaps](#known-gaps).

## Run the current backend locally

These instructions describe the **current backend-only state**, not the planned one-command full application.

### Prerequisites

- JDK 25
- Docker with Docker Compose
- Git
- No global Maven installation is required; the repository includes the Maven Wrapper.

### 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d db
```

The current Compose configuration should start PostgreSQL 17 with the local database credentials used by the application. If you have changed the Compose configuration, ensure that its database name, username, and password match the application settings below.

### 2. Start the Spring Boot API

```bash
./mvnw spring-boot:run
```

Flyway creates the database schema at application startup.

#### Optional: load the sample data

The database starts empty. To start the API with sample data (12 products and about 60 days of stock movements), activate the `seed` profile:

```bash
make run/seed
# or, without make:
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

The loader only runs if there are no products yet, so restarting with the profile never duplicates data. To start again from scratch, reset the database first (see [Reset the local database](#4-reset-the-local-database)). Without the profile (`make run`), nothing is seeded. In the full Docker Compose setup the profile is set through `SPRING_PROFILES_ACTIVE=seed`.

By default, the API is expected at:

- API base path: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health endpoint: `http://localhost:8080/actuator/health`

Database configuration is environment-driven, with local defaults:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/torch` |
| `DB_USER` | `torch` |
| `DB_PASSWORD` | `torch` |

Do not use these development defaults for a deployed environment.

### 3. Run the tests

Tests are split into two groups:

| Command | Runs | Needs PostgreSQL |
|---|---|---|
| `./mvnw test` | Unit and web-slice tests (`*Test`): controllers, services | No |
| `./mvnw verify` | The above plus integration tests (`*IT`): repository queries, application startup, API workflow, concurrency | Yes |

`./mvnw test` is the fast, database-free build intended for CI. The integration tests are heavier, so they are run manually:

```bash
docker compose up -d db
./mvnw verify
```

To run only the integration tests: `./mvnw test-compile failsafe:integration-test failsafe:verify`.

The same commands are available as `make` targets (see the `Makefile`):

| Target | What it does |
|---|---|
| `make run` | starts the database, then the API (no sample data) |
| `make run/seed` | starts the database, then the API with the `seed` profile (sample data) |
| `make test/unit` | `./mvnw clean test` (no database) |
| `make test/integration` | starts the database, then runs only the integration tests |
| `make test/all` | starts the database, then `./mvnw clean verify` |

Integration tests run against a separate `torch_test` database (profile `test`, see `src/test/resources/application-test.yaml`), never against the dev `torch` database. They truncate their tables before and after each test, so they only ever touch `torch_test`.

`torch_test` is created automatically by `docker/db/init-test-db.sql` the first time the database volume is created. If your volume already existed before that script was added, either reset it (see below) or create the database once:

```bash
docker exec torch-db psql -U torch -d torch -c "CREATE DATABASE torch_test OWNER torch;"
```

Current result: 56 unit tests (`./mvnw test`) and 23 integration tests (`./mvnw verify`), all passing. Re-run the commands and use their actual output as the source of truth for the current checkout.

### 4. Reset the local database

**Warning:** this deletes the PostgreSQL data volume.

```bash
docker compose down -v
docker compose up -d db
```

### Clean-checkout status

At the current stage, the backend can be run locally with the steps above. The required final assessment experience—one command to start the database, backend, frontend, and seed data—is **not yet implemented**. See [Planned full-application setup](#planned-full-application-setup).

## API overview

All current endpoints are under `/api`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/products` | Create a product. New products start with zero stock. |
| `GET` | `/products` | List active products, with optional filters. |
| `GET` | `/products/{id}` | Retrieve one product. |
| `PUT` | `/products/{id}` | Update product details. SKU is immutable. |
| `DELETE` | `/products/{id}` | Soft-deactivate a product; movement history is retained. |
| `POST` | `/products/{id}/movements` | Record a stock movement. |
| `GET` | `/products/{id}/movements?page=&size=` | Retrieve paged movement history, newest first. |

### Product-list filters

- `?sku=` performs an exact SKU lookup and can find deactivated products, since the SKU remains reserved.
- `?q=` performs a partial, case-insensitive match on product name or SKU among active products.
- `?lowStock=true` returns active products where `quantity_on_hand <= reorder_level`. It can be combined with `q`.
- If more than one filter is provided, the current implementation uses this precedence: `sku` before `q` before the unfiltered list; `lowStock` can be combined with `q`.

### Stock movement rules

- `RECEIPT` accepts a positive quantity and increases stock.
- `ISSUE` accepts a positive requested quantity; the service records it as a negative delta.
- Zero or negative quantities are rejected with `400 Bad Request`.
- An issue that would make stock negative is rejected with `409 Conflict`.
- A rejected issue must leave both the current balance and movement history unchanged.

**Known gap:** `ADJUSTMENT` exists in the schema and enum, but its intended signed-delta behavior is not complete in the current implementation. Until implemented and tested, do not rely on it for stock corrections. See [Known gaps](#known-gaps).

### Error responses

The API uses a central exception handler and RFC 7807 `ProblemDetail` responses. Intended mappings include:

- `400 Bad Request` for request validation errors.
- `404 Not Found` for missing resources.
- `409 Conflict` for duplicate SKUs and insufficient stock.
- `500 Internal Server Error` for unexpected errors.

Some malformed-request and unsupported-method cases still need hardening, as listed below.

## Data model

The schema is managed by Flyway migrations under `src/main/resources/db/migration`. Flyway migrations define the schema; sample data is intended to be loaded separately by an application seed loader.

### `product`

| Field | Purpose |
|---|---|
| `id` | Primary key |
| `sku` | Unique product identifier |
| `name` | Product name |
| `description` | Optional description |
| `unit` | Unit of measurement |
| `reorder_level` | Threshold used to identify low stock |
| `quantity_on_hand` | Current stock balance; starts at zero and cannot be negative |
| `active` | Whether the product is active |
| `created_at` | Creation timestamp |
| `version` | Version field for entity-level concurrency detection where used |

### `stock_movement`

| Field | Purpose |
|---|---|
| `id` | Primary key |
| `product_id` | Foreign key to `product` |
| `type` | `RECEIPT`, `ISSUE`, or `ADJUSTMENT` |
| `quantity_delta` | Signed, non-zero stock change |
| `balance_after` | Balance immediately after the movement |
| `note` | Reason or explanation |
| `created_at` | Movement timestamp |

The movement table is indexed by product and timestamp for history queries.

### Ledger invariant

For each product, the intended invariant is:

```text
quantity_on_hand = SUM(stock_movement.quantity_delta)
```

This invariant assumes that all products start at zero and every subsequent stock change is represented by a movement. The application updates the current balance and inserts its movement in the same transaction. `SeedDataLoaderIT` checks this invariant for every seeded product; end-to-end verification through Docker Compose is still planned.

## Technical decisions and trade-offs

### 1. Append-only stock-movement ledger

Every stock change is represented by a movement with a signed delta and the resulting `balance_after`. Existing movements are not edited or deleted; corrections should be recorded as new movements.

**Why:** the history explains how the current balance was reached and avoids silent overwrites.

**Trade-off:** each stock change requires a history insert as well as a balance update, and the application must keep both representations consistent.

### 2. Store the current balance on the product

`product.quantity_on_hand` is maintained alongside the movement ledger.

**Why:** product lists and low-stock filters can read the current balance without aggregating the entire movement history on every request.

**Trade-off:** the balance is denormalized. Every stock-changing path must update the product and ledger together. The application uses a single movement service and a transaction to protect this invariant.

### 3. Pessimistic row locking for stock movements

The movement service loads the product using a `SELECT ... FOR UPDATE`-style repository query inside a transaction.

**Why:** concurrent movements for the same product are serialized, preventing competing stock issues from both using the same stale balance.

**Trade-off:** concurrent writes to the same product wait for one another. This is a reasonable choice for a small, single-location inventory application. `ConcurrencyIT` verifies it: 10 parallel issues against a stock of 7 produce exactly 7 successes and 3 `409` rejections, with the balance ending at 0.

### 4. Soft deactivation instead of hard deletion

Deleting a product sets `active = false`. Normal product lists hide inactive products, while their SKU remains reserved and their movement history is retained.

**Why:** historical movements remain meaningful and linked to the original product.

**Trade-off:** inactive products remain in the database and their SKUs cannot be reused without a separate business rule.

### 5. SKU and stock are not ordinary editable product fields

SKU is immutable after creation. Product updates do not directly change the current stock balance. Stock changes must go through the movement workflow.

**Why:** this protects product identity and ensures that stock changes have an audit record.

### 6. Start products at zero stock

A new product starts with `quantity_on_hand = 0`. Initial inventory is added through a receipt movement.

**Why:** it keeps the ledger invariant true from the beginning rather than creating an unexplained starting balance.

### 7. Flyway for schema changes; application code for seed data

Flyway migrations create and evolve the schema. Demo data is loaded by an idempotent `ApplicationRunner` (`SeedDataLoader`) activated by the `seed` profile, using the normal product and movement services.

**Why:** schema history remains separate from environment-specific sample data, and seed movements exercise the same business rules as ordinary operations.

**Trade-off:** the loader uses an explicit-timestamp overload of the movement service to back-date history. The REST API cannot reach it, but it is a second entry point to keep in sync with the movement rules (the normal method delegates to it, so the rules live in one place).

### 8. DTOs and centralized error handling

The API uses request/response DTO records rather than exposing JPA entities. A central exception handler formats API errors as `ProblemDetail`.

**Why:** this keeps the external API separate from persistence details and makes error responses more consistent.

### 9. Explicit repository queries for current filters

The current search and low-stock filters use separate repository methods rather than introducing JPA Specifications.

**Why:** the number of filters is small, and explicit queries are easy to read and test.

**Trade-off:** if filtering requirements grow significantly, specifications or another composable query approach may become easier to maintain.

### 10. Injected `Clock`

The application uses an injected `Clock` for application-generated timestamps.

**Why:** tests can use a fixed clock for deterministic assertions. The same approach is intended to support reproducible, back-dated demo movements.

### 11. React + Vite instead of Next.js

The planned frontend is a React single-page application built with Vite, calling the Spring Boot API.

**Why:** this administrative interface does not require server-side rendering or SEO. Vite keeps the frontend build setup small, and Spring Boot remains the single backend for inventory rules and persistence.

**Trade-off:** the frontend and backend are separate processes during development and both must be started. The planned Docker/Nginx setup will serve the built frontend and proxy API requests to Spring Boot.

### 12. Maven instead of Gradle

Maven was chosen even though both Maven and Gradle are familiar options.

**Why:** the standard Maven lifecycle and Spring Boot dependency management are sufficient for this project's needs. The Maven Wrapper makes build commands consistent without a global Maven installation.

**Trade-off:** Gradle would also work; this is a project-fit choice, not a claim that Maven is universally better.

### 13. No additional infrastructure

The project deliberately avoids queues, caches, microservices, and frontend state-management libraries.

**Why:** the application is small, and these components would add setup and operational work without helping meet the core assessment requirements.

### 14. Integration tests run against a separate database

Integration tests use their own `torch_test` database instead of the dev    
`torch` database. They run on the same Postgres container, so this adds no new infrastructure.                                                         
                                                                          
**Why:** Tests need a known, empty starting state, and they truncate      
tables between runs. Against the dev database, they would collide with      
existing data (for example duplicate SKUs) and destroy the seed data used to try the app.

**How:**                                                                  
`docker/db/init-test-db.sql` creates `torch_test` the first time the Postgres volume is created, so a fresh `docker compose up` provides it automatically.

**Trade-off:** tests need a running Compose Postgres, and `torch_test` shares a server with the dev database, separated only by name. Testcontainers would give each run its own throwaway database, at the cost of extra dependencies and slower startup. For the same reason, integration tests (`*IT`) are kept out of `./mvnw test` and run with `./mvnw verify`, so a CI that only runs `./mvnw test` needs no database.

## Intentionally excluded functionality

The initial scope excludes:

- **Authentication and authorization:** the assessment does not require user accounts or roles.
- **Multiple warehouses or locations:** the model assumes one inventory location.
- **Fractional quantities:** the initial model supports integer quantities for countable items.
- **Hard deletion:** products are soft-deactivated to preserve history.
- **Editing or deleting stock movements:** the ledger is append-only.
- **Suppliers, purchase orders, and invoicing:** receiving stock does not require a purchasing workflow.
- **Barcode scanning and external integrations:** outside the core prompt.
- **Queues, caches, microservices, and complex client state:** unnecessary for this scale.
- **Advanced analytics and forecasting:** basic stock and movement history are the priority.

The `ADJUSTMENT` movement type is **not an intentional permanent exclusion**; its behavior is a known implementation gap and should be completed or explicitly rejected until supported.

## Known gaps

The following items were identified in the current backend and should be addressed before calling the application complete:

- `ADJUSTMENT` is currently treated like a receipt by the movement service. It should accept a non-zero signed delta, reject zero, and reject any adjustment that would make stock negative—or return `400` until the correct behavior is implemented.
- Invalid pagination values such as `page=-1` or `size=0` currently result in `500` responses rather than `400` responses.
- The exception handler needs hardening so malformed JSON, unsupported HTTP methods, and unsupported media types return appropriate `400`, `405`, or `415` responses instead of `500`.
- Optimistic-lock conflicts on product `PUT` should map to `409 Conflict`.
- Product update requests should reject unknown properties such as `sku` rather than silently ignoring them.
- `%` and `_` in the `q` search parameter act as SQL `LIKE` wildcards. Decide whether that behavior is acceptable or should be escaped.
- Seeded products have `created_at` set to the load time, while their first movement is dated about 60 days earlier (`createProduct` has no explicit-timestamp overload). Cosmetic, accepted for the demo data.
- Swagger UI shows the `filter` parameter of `GET /api/products` as required, so the endpoint cannot be called without filters from Swagger (curl works). Probably fixed by `@ParameterObject` on the controller parameter.
- The React frontend, frontend Compose service, and clean-checkout end-to-end verification are still needed.

## Planned full-application setup

The intended final reviewer experience is:

```bash
docker compose up --build
```

This is a **target, not yet complete**: today `docker compose up --build` starts `db` and `backend` (API on `http://localhost:8080`, seeded with demo data); the `frontend` service is still to be added.

The planned Compose setup will start:

- PostgreSQL 17 with a persistent volume and health check.
- The Spring Boot backend using Java 25.
- A React/Vite frontend built into static files and served by Nginx.
- A reverse proxy from frontend `/api` requests to the backend.
- An idempotent seed profile that loads sample products and movement history.

The target URLs are:

- UI: `http://localhost:3000`
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

To remove the database volume and start from an empty state:

```bash
docker compose down -v
docker compose up --build
```

**Warning:** `docker compose down -v` deletes persisted database data.

## What I would improve with another week

If another week were available, I would prioritize the following:

1. Finish and harden adjustment semantics, pagination validation, exception mapping, and optimistic-lock conflict handling.
2. Broaden the PostgreSQL integration tests to every endpoint's error mapping (the create/receive/issue/history workflow, a rejected issue leaving stock unchanged, and a concurrency test are already covered).
3. Implement and verify idempotent sample data with realistic movement history.
4. Finish the React product list, product detail, stock-adjustment form, and movement history.
5. Add the dashboard summary and a balance-over-time chart if the core workflow is stable.
6. Improve test portability with Testcontainers so tests do not depend on a separately running Compose database.
7. Add a small GitHub Actions workflow for the backend and frontend tests.
8. Consider idempotency keys for movement requests so retried client requests cannot accidentally apply the same movement twice.
9. Revisit target-count stocktake adjustments and multi-location inventory only if supported by expanded requirements.
10. Add authentication and per-user audit information if the application needs multiple users.
11. Polish the OpenAPI docs. Swagger UI works out of the box (springdoc scans the controllers), but I would add an `OpenAPI` bean with a title, version and description, and `@Operation`/`@ApiResponse` annotations so the `404` and `409` responses, which come from the exception handler and not the controller signatures, also appear in the generated documentation.

## Time spent

The work began on **Friday, 2026-10-16**. This is a running estimate and will be updated as implementation continues.

| Day / activity | Approximate time |
|---|---:|
| Initial planning with ChatGPT before starting with Claude | 3 hours |
| Friday, 2026-10-16 — implementation | 4 hours |
| Saturday, 2026-10-17 — implementation and progress review | 11 hours |
| Sunday, 2026-10-18 — work so far | 30 minutes |
| **Total so far** | **18.5 hours** |

TBD: These are approximate self-reported hours, not automated time tracking. The total is current as of Sunday morning and should be updated at the end of each work session and again before submission. The original target was a weekend project; the actual time spent so far is approximately **18.5 hours**.

## Use of AI tools

AI tools were used for planning, implementation guidance, project tracking, and documentation. The developer remained responsible for implementing the application, reviewing suggestions, running commands and tests, and deciding which changes to accept.

### ChatGPT (OpenAI)

Approximately **3 hours were spent planning with ChatGPT before implementation began with Claude**. This planning covered:

- Comparing the assessment options and choosing Inventory Tracking.
- Selecting Java + Spring Boot for the backend, React + Vite for the frontend, PostgreSQL for persistence, and Maven for the build.
- Defining the initial scope and prioritizing a complete, tested application within the Friday-to-Sunday deadline.
- Designing the `product` and `stock_movement` data model.
- Discussing the append-only movement ledger, denormalized current balance, transactional updates, pessimistic locking, seed data, testing, Docker Compose, and README expectations.
- Reviewing Claude's initial README and revising it to reflect the agreed decisions, current status, known gaps, and assessment requirements.

### Claude Code (Anthropic)

Claude Code was used as a planning and coding guide during implementation.

- **Project requirements:** Claude created a `CLAUDE.md` file containing project requirements and working guidance.
- **Progress tracking:** On Saturday, a `ROADMAP.md` file was requested to identify completed work, current progress, and the next implementation steps.
- **Code assistance:** The original project notes report that some test and application code, including movement and pagination work, was generated or modified by Claude in the repository. The developer reviewed the changes and ran tests; the exact extent of AI-generated code should be kept accurate as the project progresses.
- **Documentation:** Claude drafted the initial README from project history. The README was then reviewed and revised with ChatGPT.

### Review and responsibility

AI-generated suggestions and code were not treated as automatically correct. The developer is responsible for reviewing the implementation, making the design decisions, and verifying behavior with actual builds and tests.

Examples of implementation choices that differed from earlier suggestions:

- Use GlobalHandlerExceptionResolver to handle exceptions and return ProblemDetail responses.
- A generic `ResourceNotFoundException(String message)` was used instead of separate not-found exception classes for each entity.
- Search by SKU, not just by ID.
- Explicit repository methods such as `findLowStock()` and `searchLowStock(q)` were selected instead of JPA Specifications because the current filter set is small.
- The implementation uses a separate movement ledger while keeping the current balance on the product, with the two updated transactionally.
- Integration tests were split into one test per behavior (create product, receive, issue, current balance, history) with small setup helpers, instead of a single end-to-end flow test, so a failing test name identifies which step broke.
- Integration tests (`*IT`, run by the failsafe plugin on `./mvnw verify`) are kept out of `./mvnw test`, so the default build and a CI pipeline stay fast and database-free. The two other database-dependent classes (`ProductRepositoryIT`, `TorchApplicationIT`) were renamed to follow the same convention.
- Creation of a `Makefile` that centralizes the project commands (`make db`, `make down`, `make db/reset`, `make build`, `make run`, `make run/seed`, `make test/unit`, `make test/integration`, `make test/all`), so a reviewer does not need to remember Maven and Compose flags.
- Frontend routing: Claude suggested installing `react-router-dom`. I asked whether `react-router` alone was enough, since an earlier project of mine used only the `react-router` v7 package (`Routes`, `Route`, `Navigate` imported from `"react-router"`, no `react-router-dom`). It is: in v7 `react-router` is the main package and `react-router-dom` is only a re-export kept for older code, so the project depends on `react-router` alone. A router package is still needed next to `react-dom` (which only renders components) because the product detail page needs a real URL (`/products/:id`) for back/forward navigation, refresh and bookmarking.

## Final acceptance checklist

Before submitting, verify each item against the actual repository:

- [ ] A reviewer can follow the README from a clean checkout on macOS.
- [ ] PostgreSQL starts and Flyway applies migrations.
- [ ] Seed data loads without manual record creation.
- [ ] The frontend and backend start using the documented commands.
- [ ] Product creation, search, update, and deactivation work.
- [ ] Receipts increase stock and create movement records.
- [ ] Issues decrease stock and create movement records.
- [ ] An invalid issue is rejected without changing the balance or ledger.
- [ ] Movement history displays the recorded changes.
- [ ] The ledger sum matches each product's current balance.
- [ ] Automated tests pass.
- [ ] `docker compose up --build` works from a clean state.
- [ ] The time-spent and AI-use sections are accurate and complete.
