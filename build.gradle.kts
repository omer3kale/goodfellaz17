import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.goodfellaz17"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // ──────────────────────────────────────────────────────────────────
    // Spring Boot Core (manages transitive deps like Reactor)
    // ──────────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ──────────────────────────────────────────────────────────────────
    // Database: MSSQL (R2DBC + JDBC for migration/tooling)
    // ──────────────────────────────────────────────────────────────────
    implementation("io.r2dbc:r2dbc-mssql:1.0.1.RELEASE")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")

    // ──────────────────────────────────────────────────────────────────
    // Database: H2 for local development (no Docker needed)
    // ──────────────────────────────────────────────────────────────────
    implementation("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
    implementation("com.h2database:h2:2.2.224")

    // R2DBC pooling
    implementation("io.r2dbc:r2dbc-pool:1.0.0.RELEASE")

    // ──────────────────────────────────────────────────────────────────
    // Database Migrations (Flyway for MSSQL)
    // ──────────────────────────────────────────────────────────────────
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.flywaydb:flyway-sqlserver:9.22.3")

    // ──────────────────────────────────────────────────────────────────
    // Metrics & Monitoring
    // ──────────────────────────────────────────────────────────────────
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")

    // ──────────────────────────────────────────────────────────────────
    // Caching
    // ──────────────────────────────────────────────────────────────────
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // ──────────────────────────────────────────────────────────────────
    // Browser Automation (Selenium)
    // ──────────────────────────────────────────────────────────────────
    implementation("org.seleniumhq.selenium:selenium-java:4.15.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.6.3")

    // ──────────────────────────────────────────────────────────────────
    // Logging & Monitoring
    // ──────────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.slf4j:slf4j-api")

    // ──────────────────────────────────────────────────────────────────
    // Jackson JSON processing
    // ──────────────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ──────────────────────────────────────────────────────────────────
    // Testing: JUnit 5 + Testcontainers
    // ──────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers for integration tests (MSSQL Server)
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("org.testcontainers:mssqlserver:1.19.7")

    // ──────────────────────────────────────────────────────────────────
    // Development Tools (optional, but useful)
    // ──────────────────────────────────────────────────────────────────
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<BootRun> {
    // Allow passing arguments to the app via: ./gradlew bootRun --args='--spring.profiles.active=mssql'
    val activeProfiles: String = System.getProperty("spring.profiles.active", "mssql")
    args("--spring.profiles.active=$activeProfiles")
}

springBoot {
    mainClass = "com.goodfellaz17.GoodfellazApplication"
}

// ────────────────────────────────────────────────────────────────────
// Helper task: print gradle version
// ────────────────────────────────────────────────────────────────────
tasks.register("printGradleVersion") {
    doLast {
        println("Gradle version: ${gradle.gradleVersion}")
        println("Java version: ${JavaVersion.current()}")
    }
}
