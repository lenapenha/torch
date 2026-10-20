# Build Roadmap

Each step is small and testable. Priorities follow `CLAUDE.md`.

## Done
- [x] **A. Dependencies** — `pom.xml` resolves and compiles.
- [x] **B. App config** — `application.yaml`: datasource, `ddl-auto: validate`, `open-in-view: false`.
- [x] **C. Postgres** — `docker-compose.yml` with `db`; `contextLoads` passes.

## P0.1 — Vertical slice
1. [x] **Flyway schema** — `V1__schema.sql`. *Test:* `./mvnw test` green, tables exist.
2. [x] **`Clock` bean** — small config class so services/tests control time.
3. [x] **Entities** — `MovementType`, `Product`, `StockMovement`. *Test:* `contextLoads` (Hibernate `validate` checks them against the schema).
4. [x] **Repositories** — `ProductRepository` (with `findByIdForUpdate`, pessimistic lock), `StockMovementRepository`. *Test:* repository test.

### Build order from here: top-down, one endpoint at a time
Each endpoint is finished end to end: **controller -> DTOs -> service -> repository -> exceptions + handler -> tests** (service unit test with Mockito + fixed `Clock`; controller test with `@WebMvcTest`). The single `@RestControllerAdvice` grows one handler per slice.
Packages: `controller`, `dto`, `service`, `repository`, `model`, `enums`, `exception`, `config`.

5. [x] **Prerequisite** — constructors take `Instant createdAt`; `GlobalExceptionHandler` skeleton with 400 validation.
6. [x] **POST `/api/products`** — create (stock 0); `DuplicateSkuException` -> 409.
7. [x] **GET `/api/products/{id}`** — generic `ResourceNotFoundException(String message)` -> 404 (service supplies the detail; reused by all entities). *Note: changed from the original `ProductNotFoundException` plan — decided by Helena so we don't need a separate not-found exception per entity.*
8. **GET `/api/products`** — list + filters, in slices:
   - 8a. [x] plain list, active products only, sorted by name.
   - 8b. [x] `?sku=` exact lookup — list of 0 or 1, never 404; returns the product even if deactivated (it still owns the unique SKU).
   - 8c. [x] `?q=` partial, case-insensitive match on name or SKU, active only; `ProductFilter(sku, q, lowStock)` record carries the params; precedence `sku` > `q` > full list. Parked: `%`/`_` in `q` act as LIKE wildcards (harmless, bound parameter).
   - 8d. [x] `?lowStock=true` — `quantity_on_hand <= reorder_level`, active only, sorted by name. *Design decision (Helena):* separate repository methods (option 2) rather than JPA Specifications; `lowStock` combines with `q`. Slices:
     - 8d-1. [x] `ProductRepository.findLowStock()` (`@Query`) + repository test (test sets stock via `EntityManager` bulk update, since `Product` has no stock setter; covers below, exactly-at-level, healthy, inactive).
     - 8d-2. [x] `ProductRepository.searchLowStock(q)` (`@Query`) + repository test.
     - 8d-3. [x] Service: `lowStock=true` alone -> `findLowStock()`; with `q` -> `searchLowStock(q)`; `sku` still wins; `lowStock=false`/null ignored. Unit tests.
     - 8d-4. [x] Controller test: `?lowStock=true` (and with `q`) reaches the service via `ProductFilter`.
     - Note: Specifications were considered (scales better as filters grow, but weaker service unit tests); revisit if a fourth filter is added.
9. [x] **PUT `/api/products/{id}`** — update name/description/unit/reorderLevel; 404 if unknown. *Decision (Helena): `sku` is immutable in v1, stock and `active` are not editable here (`UpdateProductRequest` has no `sku`).* Slices: 9a controller happy path (DTO + test), 9b controller 404, 9c service + unit tests. (Optimistic-lock 409 -> Deferred.)
10. [x] **DELETE `/api/products/{id}`** — soft deactivate (204, idempotent).
11. [x] **POST `/api/products/{id}/movements`** — a) RECEIPT, b) ISSUE (`InsufficientStockException` -> 409, rejected op leaves stock unchanged); `InvalidMovementException` -> 400 (not needed so far: bean validation covers zero/negative); explicit-`Instant` overload for the seed loader (**still to do, needed by P0.3**). (ADJUSTMENT -> Deferred.) *Done 2026-10-18, 59 tests green. Known gap: ADJUSTMENT is currently treated like a RECEIPT by the service (see Deferred).*
12. [x] **GET `/api/products/{id}/movements?page=&size=`** — newest first, paged. *Done 2026-10-18: own `PageResponse<T>` record, defaults page=0 size=20, 404 for unknown product; paging-param validation -> Deferred.*
13. [x] **Integration tests (minimal)** — `@SpringBootTest` + MockMvc against separate `torch_test` DB (compose change needed): one happy path (create -> receive -> issue -> history) and one rejected-issue-leaves-stock-unchanged test. *Done 2026-10-18 (72 tests total after step 14: 55 unit + 17 integration; an earlier "76" figure was inflated by stale reports): `torch_test` created by `docker/db/init-test-db.sql` (compose mount), `application-test.yaml` + `@ActiveProfiles("test")`, `BaseIntegrationTest` truncates tables before AND after each test, `InventoryFlowIT` has 6 separate tests (create, receive, issue, balance, history, rejected issue). README updated (test section, decision 14 trade-off, contents links).*

## P0.2 — Concurrency
14. [x] **Concurrency test** — N parallel issues against stock of N-k: exactly k fail, balance never negative. *Done 2026-10-18: `ConcurrencyIT` (10 threads, stock 7 -> exactly 7x201 and 3x409, balance 0, 8 movements, ledger sum 0). Passed 5x with the lock and failed 3/3 with `findById` (mutation check). Test split: `./mvnw test` = 55 unit tests (no DB), `./mvnw verify` adds 17 `*IT` integration tests via failsafe; `ProductRepositoryTest`/`TorchApplicationTests` renamed to `*IT`.*

## P0.3 — Seed data
15. [x] **`SeedDataLoader`** — `@Profile("seed")`, idempotent, ~12 products, ~60 days of back-dated movements, uses the same service. *Test:* sum of deltas == `quantity_on_hand`.
    - [x] 15a explicit-`Instant` overload of `MovementService.recordMovement` (+ unit test).
    - [x] 15b/15c `seed/SeedDataLoader` (7 healthy, 4 low, 1 out of stock; `Random(42)`; day -1 correction movement) and `SeedDataLoaderIT` (6 tests: 12 products, ledger sum, `balance_after`, 4 low/1 out, ~60-day history, idempotent). *Done 2026-10-18: 79 tests green (56 unit + 23 integration).*
    - [x] 15d real run via `make run/seed` against `torch`: 12 products, 294 movements, invalid issue on the out-of-stock product -> 409 and stock stays 0, restart skips seeding (294 before and after). *Done 2026-10-18.*

## P0.4 — Backend container
- [x] Backend Dockerfile (multi-stage: `maven:3-eclipse-temurin-25-alpine` -> `eclipse-temurin:25-jre-alpine`, non-root user, `.dockerignore`). *Done 2026-10-18: image builds, no JVM flags baked in (use `JAVA_TOOL_OPTIONS` if needed).*
- [x] `backend` service in `docker-compose.yml` *(done 2026-10-18: `docker compose up --build` runs backend + seed against the compose DB; Helena confirmed it works; restart-persistence check passed: data survives `docker compose down`/`up`, seed skipped)* (env-driven `DB_URL`/`DB_USER`/`DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=seed`, waits for the `db` healthcheck) so the image can be run and verified before the frontend exists

## P0.5 — Frontend
*Decisions (2026-10-18): Tailwind + components-based structure (`api/`, `hooks/`, `components/`, `features/`, `pages/`); plain `fetch` wrapper, custom `useApi` hook, no state/data library; ADJUSTMENT left out of the movement form (backend gap, see Deferred); tests co-located (`X.test.tsx` beside `X.tsx`, Vitest + jsdom + RTL, setup in `src/test/setup.ts`); function first, one bounded styling pass. Scope cut: F7 = latest 20 only, F9 limited to one bounded styling pass, only 3 frontend test topics (client errors, list render + low-stock badge, movement form server error).*
*Execution order: F4 -> F6 -> F7 (done) -> Docker (P0.5b, done) -> F5 (done) -> F9 (done) -> F8 (done) -> final verification + README (done).*
- [x] F1. Scaffold: Vite react-ts + Tailwind + React Router (`react-router` v7) + dev proxy `/api` -> :8080. *Done 2026-10-18.*
- [x] F2. `api/types.ts`, `api/client.ts` (fetch wrapper -> `ApiError` with status, detail, field `errors`), `api/products.ts` (all six calls). *Done 2026-10-18; 2 tests.*
- [x] F3a. `hooks/useApi.ts` (loading/error/data/reload, ignores stale responses). *Done 2026-10-18; 4 tests (6 frontend tests total).*
- [x] F3b. `components/Loading`, `ErrorMessage`, `EmptyState`. `request` turns a network failure (`TypeError`) into `ApiError(0, 'Cannot reach the server')` and a proxy 502/503/504 without a ProblemDetail into 'The server is not available, please try again', so `ErrorMessage` never renders blank. *Done 2026-10-18; 1 extra test (network failure).*
- [x] F4. Products list: table (name, SKU, stock), `LowStockBadge` (`quantityOnHand <= reorderLevel`, 0 = out of stock), loading/error/empty states, row links to detail, plain `?q=` search input (no debounce). *Test:* mocked products render and the badge shows. *Done 2026-10-18: `ProductListPage.test.tsx`, 8 frontend tests green; list verified in the browser against the seeded backend. Possible tweak: make the 'Low item' fixture sit exactly at the reorder level so the test pins `<=`.*
- [x] F6. Product detail + receive/issue form (RECEIPT/ISSUE only; server error shown, stock and history reloaded from the server on success). *Test:* mocked 409 shows the message. *Status 2026-10-18: `ProductDetailPage` (fetch + fields + stock badge) and `features/MovementForm.tsx` (RECEIPT/ISSUE, server errors, `onSuccess` -> `reload`) implemented; `MovementForm.test.tsx` passes (mocked 409; mutation-checked), 9 frontend tests green. Manual check against the real backend passed (receive, issue, over-issue shows the server error and leaves stock unchanged). History reload is wired in F7.*
- [x] F7. Movement history: latest 20, newest first, no Prev/Next (must render ADJUSTMENT rows too). *Done 2026-10-18: `features/MovementHistory.tsx` (presentational) + a second `useApi(() => listMovements(id, 0, 20))` on `ProductDetailPage`; the form's success handler reloads both the product and the history from the server. Verified in the browser against the real backend (new rows appear on receive/issue, none on a rejected issue). No dedicated test (scope cut).*
- [x] F5. Create product form (no stock field; `description` is required by the backend; shows 409 duplicate SKU and per-field errors). *Done 2026-10-18: `features/ProductForm.tsx` (form + error display, `onSuccess(product)`), `pages/NewProductPage.tsx` (navigates to the new product's detail page), route `/products/new` before `/products/:id`, 'New product' link on the list page. Lint/type-check clean, 9 frontend tests green; verified by hand against the real backend.*
- [x] F9. Styling pass with Tailwind, one bounded pass, shared components only. *Done 2026-10-18: page shell in `App.tsx` (header + centred `max-w-4xl` main), shared `components/Field.tsx` (label + input) and `components/Button.tsx` used by `ProductForm` and `MovementForm`, white-card tables with header row and dividers, green/red movement change, detail page info card, 'New product' link styled as a button. Lint/type-check clean, 9 frontend tests green.*
- [x] F8. Edit product form (SKU read-only). *Done 2026-10-19: `ProductForm` takes an optional `product` (edit mode, calls `updateProduct` without the SKU), `pages/EditProductPage.tsx`, route `/products/:id/edit`, Edit link on the detail page. `ProductForm.test.tsx` added, 10 frontend tests green.*

## P0.5b — Remaining delivery
- [x] Frontend Dockerfile (node build -> nginx; `nginx.conf` proxies `/api` to `backend:8080` with the path unchanged, SPA fallback `try_files`) + `frontend/.dockerignore`. *Do right after F7.*
- [x] `frontend` service in `docker-compose.yml` (port 3000, `depends_on: backend`; full compose: db, backend, frontend); Makefile gets a new `make up` target (`run` unchanged, it is the local Maven loop). Root `.dockerignore` typo fixed (see below). Original: fix root `.dockerignore` typo `frontend/node_module`. *Done 2026-10-18: backend healthcheck enabled (interval 5s, retries 10, start_period 20s) and `frontend` waits for `service_healthy`; image builds; manual check of the whole flow at http://localhost:3000 passed (view, receive, issue, rejected issue, history, refresh on a detail URL, restart keeps data).*
- [x] README (run, reset, tests, API overview, design decisions, out of scope, evolution). *Run/reset/test steps use the `make` targets.*

## Delivery extras (beyond the P0 brief)
- [x] CI/CD: `.github/workflows/ci.yml` runs backend unit tests, frontend lint/tests/build and a Compose smoke test; on `main` it publishes images to GitHub Container Registry and deploys them.
- [x] Demo deployment on a Google Cloud Compute Engine `e2-micro` VM: `compose.prod.yaml` (published images, capped JVM heap, longer healthcheck start period) and `DEPLOY_SETUP.md`.
- [x] Reorganised `Makefile`: targets grouped by purpose with `make help`; README steps use the `make` targets.

## P0.6 — Acceptance
Verify each item against the actual repository before submitting (moved here from the README).
- [x] `make up` (`docker compose up --build`) starts with seeded data from a clean state; reviewer can create products, receive/issue stock, see an invalid issue rejected, and inspect history.
- [x] PostgreSQL starts and Flyway applies migrations.
- [x] Seed data loads without manual record creation.
- [x] The frontend and backend start using the documented commands.
- [x] Product creation, search, update, and deactivation work. *Deactivation is API-only; the UI has no button for it.*
- [x] Receipts increase stock and create movement records.
- [x] Issues decrease stock and create movement records.
- [x] An invalid issue is rejected without changing the balance or ledger.
- [x] Movement history displays the recorded changes.
- [x] The ledger sum matches each product's current balance.
- [x] Automated tests pass.

## P1
- [ ] `GET /api/summary` dashboard summary (the low-stock filter is done, see 8d)

## Optional (only after all P0)
- [ ] Balance chart, global activity feed, Testcontainers
- [x] GitHub Actions (see Delivery extras)

## Not doing
- Authentication

## Deferred — not done (do only after everything above)
Essential-only rule: anything that doesn't fit in the remaining time goes here, in this order.
- [ ] **ADJUSTMENT movements** — until implemented, `MovementService` treats ADJUSTMENT as a positive delta (silently adds stock); consider rejecting it with 400 meanwhile. nonzero signed delta, wrong-sign/zero rejected, result below zero -> 409; adjust option in the frontend form.
- [ ] **Optimistic-lock conflict on PUT -> 409** (`ObjectOptimisticLockingFailureException` handler; needs `version` in the update flow).
- [ ] **6b. Exception handler hardening** — `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`; replace `handleValidation` with an override of `handleMethodArgumentNotValid` (delete the old `@ExceptionHandler` method, return via `handleExceptionInternal`). Until done, the catch-all turns malformed JSON / wrong method / unsupported media type into 500 instead of 400/405/415. *Test first:* `shouldReturnBadRequestWhenBodyIsMalformedJson`.
- [ ] **Paging param validation** — `?page=-1` / `?size=0` on `GET /products/{id}/movements` currently reach `PageRequest.of` and become 500 via the catch-all; should be 400 (`@Validated` + `@Min`, handler for `ConstraintViolationException`; consider a max size).
- [ ] Broader API integration tests (every endpoint's error mapping).
- [ ] Reject unknown properties on `UpdateProductRequest` (e.g. a `sku` in the body) with 400.
- [ ] Seeded products' `created_at` is the load time, not 60 days ago (needs an explicit-`Instant` overload of `createProduct`).
- [ ] `lowStock` follow-ups: `%`/`_` escaping in `q`; Specifications if a fourth filter appears.
- [ ] **Swagger UI: `GET /api/products` filters** — Swagger UI shows `filter` as a required query param, and sending it empty fails with "For 'filter': Required field is not provided", so the endpoint can't be called without filters (curl works). Likely cause: `ProductController.listProducts(ProductFilter filter)` binds a plain record and springdoc documents it as one object param. Fix to try: annotate with `@ParameterObject` (springdoc) so `q`, `sku`, `lowStock` show as separate optional params. Also review the other endpoints in Swagger UI (movements `page`/`size`, request bodies, error responses).
- [ ] **Movement history Prev/Next paging in the UI** — backend paging works; the UI shows only the latest 20.
- [ ] **Frontend tests beyond the three core topics** — debounced search calls the API with `q`, create-product form errors, hook/component edge cases.
- [ ] **Index for the `q` search** — partial matches (`lower(...) like '%q%'`) scan the product table; exact SKU lookups already use the unique index. Fine at this scale.
- [ ] **Integration tests in CI only in specific flows** — for example a nightly or manual workflow, not the deploy path, so deployments stay fast (needs a PostgreSQL service container or Testcontainers).
