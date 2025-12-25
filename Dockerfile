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

# Copy the specific JAR file (avoids glob issues)
COPY --from=build /app/target/goodfellaz17-provider-1.0.0-SNAPSHOT.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run application
EXPOSE 8080 10000
ENV JAVA_OPTS="--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Xmx512m -Xms256m -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod

# Use shell form so $PORT and $JAVA_OPTS expand properly
CMD java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar
