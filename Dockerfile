# --- BUILD ---
FROM maven:3-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .

# cached layer: only reruns when pom.xml changes
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests -DskipITs

# --- RUNTIME ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# non-root system user/group, so the app doesn't run as root
RUN addgroup -S torch && adduser -S -G torch -u 1001 torch

COPY --from=build /app/target/torch-0.0.1-SNAPSHOT.jar torch.jar
USER torch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "torch.jar"]