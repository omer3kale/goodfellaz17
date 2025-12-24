# GOODFELLAZ17 SMM API - Production Dockerfile
# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy application
COPY --from=build /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
