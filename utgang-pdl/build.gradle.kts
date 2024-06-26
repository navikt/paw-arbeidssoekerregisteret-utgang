
plugins {
    kotlin("jvm")
    id("com.google.cloud.tools.jib") version "3.4.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    application
}
val javaVersion: String by project
val jvmVersion = JavaVersion.valueOf("VERSION_$javaVersion")
val image: String? by project

val schema by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(project(":kafka-key-generator-client"))
    implementation(project(":main-avro-schema-classes"))
    implementation(pawObservability.bundles.ktorNettyOpentelemetryMicrometerPrometheus)

    implementation(loggingLibs.logbackClassic)
    implementation(loggingLibs.logstashLogbackEncoder)
    implementation(navCommon.log)

    implementation(pawUtils.kafkaStreams)
    implementation(pawUtils.kafka)
    implementation(pawUtils.hopliteConfig)

    implementation(apacheAvro.kafkaAvroSerializer)
    implementation(apacheAvro.kafkaStreamsAvroSerde)
    implementation(apacheAvro.avro)
    implementation(orgApacheKafka.kafkaStreams)
    implementation(arbeidssoekerRegisteret.internalEvents)
    implementation(pawClients.pawPdlClient)

    implementation(navCommon.tokenClient)

    implementation(jacskon.jacksonDatatypeJsr310)
    implementation(jacskon.jacksonModuleKotlin)
    implementation(jacskon.ktorSerialization)

    implementation(ktorClient.contentNegotiation)
    implementation(ktorClient.core)
    implementation(ktorClient.cio)

    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.6.0")
    testImplementation(orgApacheKafka.streamsTest)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(jvmVersion.majorVersion)
    }
}

application {
    mainClass = "no.nav.paw.arbeidssoekerregisteret.utgang.pdl.StartupKt"
}

jib {
    from.image = "ghcr.io/navikt/baseimages/temurin:${jvmVersion.majorVersion}"
    val actualImage = "${image ?: rootProject.name}:${project.version}"
    to.image = actualImage
    container {
        environment = mapOf(
            "IMAGE_WITH_VERSION" to actualImage
        )
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Title"] = rootProject.name
        attributes["Main-Class"] = application.mainClass.get()
    }
}