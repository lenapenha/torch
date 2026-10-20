# Torch — Inventory Tracking System

Torch is a small-business inventory tracking application. It is designed to answer two questions:

1. **What stock do we have right now?**
2. **How has that stock changed over time?**

The core design is an append-only stock-movement ledger. Every inventory change is recorded, and the product's current balance is maintained alongside that history.

A demo of the application is deployed on a Google Cloud Compute Engine `e2-micro` VM: http://34.55.22.240:3000

> **Status:** functionally complete and verified from a clean Docker state. 56 backend unit tests, 23 integration tests and 10 frontend tests pass. The `make up` flow was checked by hand and by the CI smoke test, but not yet from a fresh `git clone` on another machine. See [Known gaps](#known-gaps).

## Contents

- [Technology stack](#technology-stack)
- [Current functionality](#current-functionality)
- [Run the full application with Docker](#run-the-full-application-with-docker)
- [Run the backend locally](#run-the-backend-locally)
- [API overview](#api-overview)
- [Data model](#data-model)
- [Technical decisions and trade-offs](#technical-decisions-and-trade-offs)
- [Run the tests](#2-run-the-tests)
- [Intentionally excluded functionality](#intentionally-excluded-functionality)
- [Known gaps](#known-gaps)
- [Continuous integration and deployment](#continuous-integration-and-deployment)
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

### Frontend

- React
- TypeScript
- Vite
- React Router (`react-router` v7)
- Tailwind CSS
- Vitest + React Testing Library

### Local development and delivery

- Docker Compose for PostgreSQL, the backend and the Nginx-served frontend
- Environment-based database configuration

### Why this stack?

Java and Spring Boot because I know them well and could focus on the domain rules. React with Vite as a client-side rendered single-page app, rather than Next.js, because this is an administrative UI over a separate REST API, with no need for server-side rendering or SEO; React Router handles client-side navigation. Maven rather than Gradle because its conventional lifecycle is enough, and the Maven Wrapper makes builds reproducible without a global install.

## Current functionality

- Product create, retrieve, list, update and soft deactivation, with unique SKUs, search and a low-stock filter.
- Stock receipts and issues, with paged per-product movement history.
- Transactional stock updates under a pessimistic row lock.
- RFC 7807 `ProblemDetail` errors from one central handler; DTO records instead of entities.
- Flyway schema; idempotent `seed` profile loader (12 products, about 60 days of back-dated movements) that goes through the normal services.
- React UI: product list with search and low-stock badges, create and edit forms (SKU read-only), a receive/issue form that shows server errors, and the latest 20 movements.
- GitHub Actions pipeline that tests, smoke-tests Compose, publishes images and deploys (see [Continuous integration and deployment](#continuous-integration-and-deployment)).

Limitations are listed under [Known gaps](#known-gaps).

## Run the full application with Docker

The only requirement is Docker with Docker Compose (plus Git to clone the repository): the backend and frontend are built inside containers, so no local JDK, Maven or Node is needed.

```bash
make up
```

This builds and starts three services in the background (follow the logs with `make logs`, stop with `make down`). The frontend waits until the backend healthcheck passes, so the first load works without errors; allow about half a minute on the first start.

Compose runs PostgreSQL (persistent volume, health check), the seeded Spring Boot backend, and the React build served by Nginx, which proxies `/api` to the backend.

URLs:

- UI: `http://localhost:3000`
- API: `http://localhost:8080/api/products`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

To wipe the database volume and start again:

```bash
make reset
```

**Warning:** `make reset` deletes persisted database data.

## Run the backend locally

The same application broken into pieces: PostgreSQL in Docker, the API on the host. This is the development loop; for just trying the app, use [Run the full application with Docker](#run-the-full-application-with-docker) above.

### Prerequisites

- Docker with Docker Compose (runs PostgreSQL)
- Git
- JDK 25, to run or test the backend on the host. No global Maven installation is required; the repository includes the Maven Wrapper.

**Before you start:** run `make down` if you used `make up`. Its backend container holds port 8080, so `make run` would fail with "Port 8080 was already in use". To wipe the database in this flow, use `make db/reset`, not `make reset`.

### 1. Start the API

Start it either without or with sample data (12 products and about 60 days of stock movements):

```bash
make run        # no sample data
make run/seed   # with sample data
```

Both start PostgreSQL in Docker first, then the API (Flyway creates the schema). The API keeps the terminal until `Ctrl+C`, so use a second terminal for anything else. To switch between the two commands, stop the running one first (port 8080).

The seed loader only runs if there are no products yet, so it never duplicates data. To start again from scratch, reset the database first (see [Reset the local database](#3-reset-the-local-database)). Compose sets `SPRING_PROFILES_ACTIVE=seed`.

The API is at `http://localhost:8080/api/products`, with Swagger UI at `/swagger-ui.html` and health at `/actuator/health`.

Database configuration is environment-driven, with local defaults:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/torch` |
| `DB_USER` | `torch` |
| `DB_PASSWORD` | `torch` |

Do not use these defaults in a deployed environment.

### 2. Run the tests

Unit tests (`*Test`) are database-free and are what CI runs. Integration tests (`*IT`) need PostgreSQL and are run manually. Run `make` to list all targets:

| Target | What it does | Needs PostgreSQL |
|---|---|---|
| `make test/unit` | Controller and service tests | No |
| `make test/integration` | Repository queries, startup, API workflow, concurrency | Yes (started for you) |
| `make test/all` | Unit and integration tests | Yes (started for you) |
| `make test/frontend` | Frontend tests (Vitest + React Testing Library) | No |

Integration tests run against a separate `torch_test` database (profile `test`), never the dev `torch` database, because they truncate tables between tests. `docker/db/init-test-db.sql` creates it the first time the volume is created. If your volume predates that script, reset it (below) or run:

```bash
make db/create-test
```

### 3. Reset the local database

**Warning:** this deletes the PostgreSQL data volume.

```bash
make db/reset
```

## API overview

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
- Precedence when combined: `sku`, then `q`, then the unfiltered list; `lowStock` can be combined with `q`.

### Stock movement rules

- `RECEIPT` accepts a positive quantity and increases stock.
- `ISSUE` accepts a positive requested quantity; the service records it as a negative delta.
- Zero or negative quantities are rejected with `400 Bad Request`.
- An issue that would make stock negative is rejected with `409 Conflict`.
- A rejected issue leaves both the balance and the movement history unchanged.
- `ADJUSTMENT` is not complete yet; see [Known gaps](#known-gaps).

### Error responses

The API uses a central exception handler and RFC 7807 `ProblemDetail` responses. The mappings are:

- `400 Bad Request` for request validation errors.
- `404 Not Found` for missing resources.
- `409 Conflict` for duplicate SKUs and insufficient stock.
- `500 Internal Server Error` for unexpected errors.

## Data model

Flyway migrations under `src/main/resources/db/migration` define the schema; sample data comes from the seed loader.

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

Products start at zero and every stock change is a movement, written in the same transaction as the balance update. `SeedDataLoaderIT` checks the invariant for every seeded product.

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

SKU is immutable, products start at zero stock, and product updates never change the balance. Initial inventory and every later change go through the movement workflow.

**Why:** it protects product identity and keeps the ledger invariant true from the first row, with no unexplained starting balance.

### 6. Flyway for schema changes; application code for seed data

Flyway migrations create and evolve the schema. Demo data is loaded by an idempotent `ApplicationRunner` (`SeedDataLoader`) activated by the `seed` profile, using the normal product and movement services.

**Why:** schema history remains separate from environment-specific sample data, and seed movements exercise the same business rules as ordinary operations.

**Trade-off:** the loader uses an explicit-timestamp overload of the movement service to back-date history. The REST API cannot reach it, but it is a second entry point to keep in sync with the movement rules (the normal method delegates to it, so the rules live in one place).

### 7. DTOs and centralized error handling

The API uses request/response DTO records rather than exposing JPA entities. A central exception handler formats API errors as `ProblemDetail`.

**Why:** this keeps the external API separate from persistence details and makes error responses more consistent.

### 8. Explicit repository queries for current filters

The current search and low-stock filters use separate repository methods rather than introducing JPA Specifications.

**Why:** the number of filters is small, and explicit queries are easy to read and test.

**Trade-off:** if filtering requirements grow significantly, specifications or another composable query approach may become easier to maintain.

### 9. Injected `Clock`

The application uses an injected `Clock` for application-generated timestamps.

**Why:** tests can use a fixed clock for deterministic assertions. The seed loader back-dates its movements through an explicit-timestamp overload of the movement service instead.

### 10. No additional infrastructure

The project deliberately avoids queues, caches, microservices, and frontend state-management libraries.

**Why:** the application is small, and these components would add setup and operational work without helping meet the core assessment requirements.

### 11. Integration tests run against a separate database

Integration tests use their own `torch_test` database on the same Postgres container, so there is no new infrastructure.

**Why:** they need a known, empty starting state and truncate tables between tests. Against the dev database they would collide with existing data and destroy the seed data.

**Trade-off:** tests need a running Compose Postgres, and `torch_test` is separated from the dev database only by name. Testcontainers would give each run a throwaway database, at the cost of extra dependencies and slower startup. For the same reason `*IT` classes run on `./mvnw verify`, not `./mvnw test`, so CI needs no database.

## Intentionally excluded functionality

Beyond the exclusions in the design decisions above (hard deletion, movement edits, extra infrastructure), the scope leaves out:

- **Authentication and authorization:** the assessment does not require user accounts or roles.
- **Multiple warehouses or locations:** the model assumes one inventory location.
- **Fractional quantities:** the initial model supports integer quantities for countable items.
- **Suppliers, purchase orders, and invoicing:** receiving stock does not require a purchasing workflow.
- **Barcode scanning and external integrations:** outside the core prompt.
- **Advanced analytics and forecasting:** basic stock and movement history are the priority.

## Known gaps

Items to address before calling the application complete:

- `ADJUSTMENT` is currently treated like a receipt by the movement service. It should accept a non-zero signed delta, reject zero, and reject any adjustment that would make stock negative—or return `400` until the correct behavior is implemented.
- Invalid pagination values such as `page=-1` or `size=0` currently result in `500` responses rather than `400` responses.
- The exception handler needs hardening so malformed JSON, unsupported HTTP methods, and unsupported media types return appropriate `400`, `405`, or `415` responses instead of `500`.
- Optimistic-lock conflicts on product `PUT` should map to `409 Conflict`.
- Product update requests should reject unknown properties such as `sku` rather than silently ignoring them.
- `%` and `_` in the `q` search parameter act as SQL `LIKE` wildcards. Decide whether that behavior is acceptable or should be escaped.
- Seeded products have `created_at` set to the load time, while their first movement is dated about 60 days earlier (`createProduct` has no explicit-timestamp overload). Cosmetic, accepted for the demo data.
- Swagger UI shows the `filter` parameter of `GET /api/products` as required, so the endpoint cannot be called without filters from Swagger (curl works). Probably fixed by `@ParameterObject` on the controller parameter.
- The `q` search scans the product table (no index for partial matches). Exact SKU lookups use the unique index. Fine at this scale.
- The UI shows only the latest 20 movements (no paging) and has no UI for `ADJUSTMENT` or deactivation (the API supports both), and there is no dashboard summary or balance chart.

## Continuous integration and deployment

The workflow is `.github/workflows/ci.yml`. It runs on pushes and pull requests to `main` (documentation-only changes are ignored) and can also be started manually. Jobs:

| Job | What it does |
|---|---|
| `backend` | Java 25, `./mvnw -B test` (unit tests only, no database; integration tests are run locally with `./mvnw verify`) |
| `frontend` | Node 22, `npm ci`, lint, tests and production build |
| `docker` | Compose smoke test, after `backend` and `frontend`: `docker compose up --build -d --wait`, then checks `/actuator/health` and that `/api/products` returns seeded products through the Nginx proxy |
| `publish` | On `main` only: builds the backend and frontend images and pushes them to GitHub Container Registry (`ghcr.io/lenapenha/torch-backend` and `torch-frontend`, tagged `latest` and with the commit SHA) |
| `deploy` | On `main` only: copies `compose.prod.yaml` to the Compute Engine VM over SSH, writes `DB_PASSWORD` to a `.env` file there, pulls the images and restarts the stack |

`compose.prod.yaml` runs the published images instead of building them. It is tuned for the small `e2-micro` VM: capped JVM heap and a longer backend healthcheck start period, because a cold start with seeding takes a few minutes there.

The deploy job needs a GitHub environment named `production` with the secrets `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_APP_PATH` and `DB_PASSWORD`. The VM needs Docker, a user that can run it and that accepts the deploy SSH key, and an open firewall port 3000. The demo runs over plain HTTP.

## What I would improve with another week

If another week were available, I would prioritize the following:

1. Finish and harden adjustment semantics, pagination validation, exception mapping, and optimistic-lock conflict handling.
2. Broaden the PostgreSQL integration tests to every endpoint's error mapping (the create/receive/issue/history workflow, a rejected issue leaving stock unchanged, and a concurrency test are already covered).
3. Add paged movement history, a deactivate action and a stock-adjustment form to the UI.
4. Make seeded `created_at` values consistent with the back-dated movements.
5. Add the dashboard summary and a balance-over-time chart if the core workflow is stable.
6. Improve test portability with Testcontainers so tests do not depend on a separately running Compose database.
7. Run the integration tests in CI only in specific flows (for example a separate nightly or manual workflow, not the deploy path), so they don't slow down deployment. They need a PostgreSQL service container or Testcontainers. Also add HTTPS in front of the deployed demo.
8. Add authentication and per-user audit information if the application needs multiple users.
9. Polish the OpenAPI docs. Swagger UI works out of the box (springdoc scans the controllers), but I would add an `OpenAPI` bean with a title, version and description, and `@Operation`/`@ApiResponse` annotations so the `404` and `409` responses, which come from the exception handler and not the controller signatures, also appear in the generated documentation.

## Time spent

Approximately **31 hours** in total (self-reported, not automated time tracking), including about 3 hours of planning before implementation started.

## Use of AI tools

I used AI tools for planning, as a pair-programming assistant, for project tracking, and for documentation. I made the design decisions, reviewed every suggestion, ran the commands and tests, and decided which changes to accept.

### ChatGPT (OpenAI)

I spent approximately **3 hours planning with ChatGPT before starting implementation with Claude**. The planning covered:

- Comparing the assessment options and choosing Inventory Tracking.
- Selecting Java + Spring Boot for the backend, React + Vite for the frontend, PostgreSQL for persistence, and Maven for the build.
- Defining the initial scope and prioritizing a complete, tested application within the deadline.
- Designing the `product` and `stock_movement` data model.
- Discussing the append-only movement ledger, denormalized current balance, transactional updates, pessimistic locking, seed data, testing, Docker Compose, and README expectations.
- Reviewing Claude's initial README and revising it to reflect the decisions I agreed, the current status, known gaps, and assessment requirements.

### Claude Code (Anthropic)

I used Claude Code as an assistant for planning, implementation and review.

- **Project requirements:** Claude wrote a `CLAUDE.md` file that records the project requirements and the working method (build top-down, one endpoint at a time, with tests before moving on).
- **Progress tracking:** I asked for a `ROADMAP.md` file to identify completed work, current progress, and the next implementation steps.
- **Code assistance:** Some test and application code, including movement and pagination work, was generated or modified by Claude in the repository. I reviewed the changes and ran the tests.
- **Documentation:** Claude drafted the initial README from the project history. I then reviewed and revised it with ChatGPT.

### Review and responsibility

I did not treat AI-generated suggestions and code as automatically correct. I am responsible for reviewing the implementation, making the design decisions, and verifying behavior with actual builds and tests.

Examples of implementation choices where I differed from earlier suggestions:

- I used `GlobalExceptionHandler` to handle exceptions and return `ProblemDetail` responses.
- I used a generic `ResourceNotFoundException(String message)` instead of separate not-found exception classes for each entity.
- I added search by SKU, not just by ID.
- I split the integration tests into one test per behavior (create product, receive, issue, current balance, history) with small setup helpers, instead of a single end-to-end flow test, so a failing test name identifies which step broke.
- Frontend routing: Claude suggested installing `react-router-dom`. I asked whether `react-router` alone was enough, since an earlier project of mine used only the `react-router` v7 package (`Routes`, `Route`, `Navigate` imported from `"react-router"`, no `react-router-dom`). It is: in v7 `react-router` is the main package and `react-router-dom` is only a re-export kept for older code, so the project depends on `react-router` alone. A router package is still needed next to `react-dom` (which only renders components) because the product detail page needs a real URL (`/products/:id`) for back/forward navigation, refresh and bookmarking.
