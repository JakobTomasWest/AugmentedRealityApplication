val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.20"
    id("io.ktor.plugin") version "3.1.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

group = "com.augmentedreality"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    docker {
        localImageName.set("augmented-reality-server")
        imageTag.set("1.0.0")
        jreVersion.set(JavaVersion.VERSION_21)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
    java.targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // --- Ktor Server ---
    implementation("io.ktor:ktor-server-auth:3.1.2")
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-auth-jwt:3.1.2")
    // using a helper method here to handle logins
    // using JWTs with the client itself
    implementation("io.ktor:ktor-server-auth-ldap:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-server-netty:3.1.2")
    implementation("io.ktor:ktor-utils:3.1.2")

    // --- Logging / Utils ---
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // --- Ktor Client ---
    implementation("io.ktor:ktor-client-core-jvm:3.1.2")
    implementation("io.ktor:ktor-client-apache:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")

    // --- Tests ---
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.1.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.ktor:ktor-client-core:3.1.2")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    testImplementation("io.ktor:ktor-client-auth:3.1.2")
}
