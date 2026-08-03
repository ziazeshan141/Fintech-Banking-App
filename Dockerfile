# ==========================================
# STAGE 1: Build stage with Maven and JDK 17
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml to download dependencies and cache layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build production JAR (skipping unit tests for faster build)
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Lightweight JRE Runtime environment
# ==========================================
FROM eclipse-temurin:17-jre-alpine

# Create dedicated non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy compiled JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Adjust ownership
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Configure Environment Variables for Spring Boot
ENV SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC \
    SPRING_DATASOURCE_USERNAME=bankuser \
    SPRING_DATASOURCE_PASSWORD=bankpassword \
    SPRING_JPA_HIBERNATE_DDL_AUTO=update

# Container Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]