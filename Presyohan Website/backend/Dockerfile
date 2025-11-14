## Multi-stage build for Spring Boot (Java 17)
## Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the app (skip tests for faster image builds)
RUN mvn -q clean package -DskipTests

## Stage 2: Runtime image (Debian-based JRE for broader network/TLS support)
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Render sets PORT; Spring Boot reads server.port=${PORT:8080}
ENV PORT=8080

# Prefer IPv4 to avoid IPv6 routing issues on some platforms
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

# Ensure CA certificates are present for TLS connections (Supabase requires TLS)
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]