plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.mysample"
version = "0.1.0-SNAPSHOT"
description = "Customer service - hexagonal Spring Boot REST microservice (PostgreSQL, Vault, external REST API)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    // Inbound REST API
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.webmvc.ui)

    // Outbound REST client (RestClient + HTTP Service Clients) -> JSONPlaceholder
    implementation(libs.spring.boot.starter.restclient)

    // Persistence: Spring Data JPA on PostgreSQL, schema owned by Flyway
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // Secrets: database credentials are read from HashiCorp Vault (spring.config.import=vault://)
    implementation(libs.spring.cloud.starter.vault.config)

    // Security: inbound JWT resource server
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // Resilience: retry + circuit breaker (annotations need AspectJ)
    implementation(libs.resilience4j.spring.boot)
    implementation(libs.spring.boot.starter.aspectj)

    // Observability: health, metrics, tracing
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    runtimeOnly(libs.micrometer.registry.prometheus)

    // Boilerplate reduction (compile time only). Rules for allowed annotations: lombok.config
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Type-safe mappers generated at compile time. The binding lets MapStruct see Lombok-generated accessors.
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok.mapstruct.binding)

    // Redis Cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Tests (each test starter brings spring-boot-starter-test transitively)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers) // real PostgreSQL in tests (needs Docker)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly(libs.junit.platform.launcher)

}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation",
            "-Amapstruct.suppressGeneratorTimestamp=true", // reproducible builds: no timestamp in generated code
        )
    )
}

tasks.test {
    useJUnitPlatform()
}

// Only build the executable jar, with a fixed name so the Dockerfile can find it.
tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

// LOCAL ONLY: feed the app's own variables from .env (shared with docker compose) into bootRun,
// so the app and Vault always agree on the AppRole ids. Values set here override stale OS variables.
// Allowlist: the app never receives the superuser or schema-owner passwords.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // dev only: default to "local" unless the caller already chose a profile
    if (System.getenv("SPRING_PROFILES_ACTIVE").isNullOrBlank()) {
        environment("SPRING_PROFILES_ACTIVE", "local")
    }
    val appVariables = setOf("VAULT_APP_ROLE_ID", "VAULT_APP_SECRET_ID", "DB_NAME")
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .map { it.split("=", limit = 2).let { (key, value) -> key.trim() to value.trim() } }
            .filter { (key, _) -> key in appVariables }
            .forEach { (key, value) -> environment(key, value) }
    }
}
