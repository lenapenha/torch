# Inventory Tracking System: project commands.
# Run `make` (or `make help`) to list every target.

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this list of targets
	@awk 'BEGIN {FS = ":.*## "} \
		/^# == / {sub(/^# == /, ""); printf "\n\033[1m%s\033[0m\n", $$0} \
		/^[a-zA-Z0-9_\/-]+:.*## / {printf "  \033[36mmake %-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo

# == Full application in Docker (needs only Docker)

.PHONY: up
up: ## Build and start database, backend (seeded) and frontend in the background
	docker compose up --build -d

.PHONY: logs
logs: ## Follow the logs of all containers (Ctrl+C to stop following)
	docker compose logs -f

.PHONY: down
down: ## Stop all containers, keeping the database data
	docker compose down

.PHONY: reset
reset: ## Delete the database volume and start the full application from scratch
	docker compose down -v
	$(MAKE) up

.PHONY: up/backend
up/backend: ## Build and start only the database and backend containers, waiting until healthy
	docker compose up --build -d --wait backend

# == Local development (needs JDK 25; the database runs in Docker)

.PHONY: db
db: ## Start only PostgreSQL and wait until it is healthy
	docker compose up -d --wait db

.PHONY: db/create-test
db/create-test: db ## Create the torch_test database in an existing volume that predates it
	docker exec torch-db psql -U torch -d torch -c "CREATE DATABASE torch_test OWNER torch;"

.PHONY: db/reset
db/reset: ## Delete the database volume and start an empty PostgreSQL
	docker compose down -v
	$(MAKE) db

.PHONY: run
run: db ## Start PostgreSQL, then the API on the host (no sample data)
	./mvnw spring-boot:run

.PHONY: run/seed
run/seed: db ## Start PostgreSQL, then the API on the host with sample data
	./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

.PHONY: build
build: ## Package the backend jar, skipping tests
	./mvnw clean package -DskipTests

# == Tests

.PHONY: test/unit
test/unit: ## Backend unit tests (no database needed)
	./mvnw clean test

.PHONY: test/integration
test/integration: db ## Backend integration tests only (starts PostgreSQL)
	./mvnw clean test-compile failsafe:integration-test failsafe:verify

.PHONY: test/all
test/all: db ## Backend unit and integration tests (starts PostgreSQL)
	./mvnw clean verify

.PHONY: test/frontend
test/frontend: ## Frontend tests (Vitest + React Testing Library)
	cd frontend && npm install && npm test
