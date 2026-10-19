.PHONY: db
db:
	docker compose up -d --wait db

.PHONY: db/reset
db/reset:
	docker compose down -v
	make db

.PHONY: down
down:
	docker compose down

.PHONY: build
build:
	./mvnw clean package -DskipTests

.PHONY: up
up:
	docker compose up --build -d

.PHONY: run
run:
	make db
	./mvnw spring-boot:run

.PHONY: run/seed
run/seed:
	make db
	./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

.PHONY: test/unit
test/unit:
	./mvnw clean test

.PHONY: test/integration
test/integration:
	make db
	./mvnw clean test-compile failsafe:integration-test failsafe:verify

.PHONY: test/all
test/all:
	make db
	./mvnw clean verify
