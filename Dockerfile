# GOODFELLAZ17 SMM API - Production Dockerfile

# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the specific JAR file (avoids glob issues)
COPY --from=build /app/target/goodfellaz17-provider-1.0.0-SNAPSHOT.jar app.jar

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD ["wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]

# Expose application ports
EXPOSE 8080 10000
ENV JAVA_OPTS="--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Xmx1024m -Xms512m -XX:+UseContainerSupport -XX:MaxMetaspaceSize=256m"
ENV SPRING_PROFILES_ACTIVE=prod

# Use exec-form so Java can receive OS signals via the script
ENTRYPOINT ["/app/entrypoint.sh"]
