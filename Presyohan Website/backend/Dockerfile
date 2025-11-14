## Multi-stage build for Spring Boot (Java 17)
## Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the app (skip tests for faster image builds)
RUN mvn -q clean package -DskipTests

## Stage 2: Runtime image (smaller JRE)
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Render sets PORT; Spring Boot reads server.port=${PORT:8080}
ENV PORT=8080
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]