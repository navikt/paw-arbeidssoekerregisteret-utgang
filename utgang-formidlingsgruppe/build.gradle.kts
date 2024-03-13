import com.github.davidmc24.gradle.plugin.avro.GenerateAvroProtocolTask

plugins {
    kotlin("jvm")
    id("com.google.cloud.tools.jib") version "3.4.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    application
}

val pawUtilsVersion = "24.02.06.10-1"
val kafkaStreamsVersion = "3.6.0"
val arbeidssokerregisteretVersion = "1.8062260419.22-1"
val jvmVersion = JavaVersion.VERSION_21
val image: String? by project

val schema by configurations.creating {
    isTransitive = false
}

dependencies {
    schema("no.nav.paw.arbeidssokerregisteret.api:main-avro-schema:$arbeidssokerregisteretVersion")
    implementation(pawObservability.bundles.ktorNettyOpentelemetryMicrometerPrometheus)
    implementation("no.nav.paw.hoplite-config:hoplite-config:$pawUtilsVersion")

    // Kafka
    implementation("no.nav.paw.kafka:kafka:$pawUtilsVersion")
    implementation("no.nav.paw.kafka-streams:kafka-streams:$pawUtilsVersion")
    implementation("io.confluent:kafka-avro-serializer:7.6.0")
    implementation("io.confluent:kafka-streams-avro-serde:7.6.0")
    implementation("org.apache.avro:avro:1.11.3")
    implementation("org.apache.kafka:kafka-clients:$kafkaStreamsVersion")
    implementation("org.apache.kafka:kafka-streams:$kafkaStreamsVersion")

    //Jackson
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(jvmVersion.majorVersion)
    }
}

application {
    mainClass = "org.example.AppKt"
}

jib {
    from.image = "ghcr.io/navikt/baseimages/temurin:${jvmVersion.majorVersion}"
    to.image = "${image ?: rootProject.name }:${project.version}"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
    source(zipTree(schema.singleFile))
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Title"] = rootProject.name
        attributes["Main-Class"] = application.mainClass.get()
    }
}