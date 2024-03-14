plugins {
    kotlin("jvm")
    id("com.google.cloud.tools.jib") version "3.4.1"
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

    implementation(pawUtils.kafkaStreams)
    implementation(pawUtils.kafka)
    implementation(pawUtils.hopliteConfig)

    implementation(apacheAvro.kafkaAvroSerializer)
    implementation(apacheAvro.kafkaStreamsAvroSerde)
    implementation(apacheAvro.avro)
    implementation(orgApacheKafka.kafkaStreams)

    implementation(jacskon.jacksonDatatypeJsr310)
    implementation(jacskon.jacksonModuleKotlin)

    implementation(ktorClient.contentNegotiation)
    implementation(ktorClient.core)
    implementation(ktorClient.cio)
    implementation(jacskon.ktorSerialization)


}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(jvmVersion.majorVersion)
    }
}

application {
    mainClass = "no.nav.paw.arbeidssoekerregisteret.app.Startup"
}

jib {
    from.image = "ghcr.io/navikt/baseimages/temurin:${jvmVersion.majorVersion}"
    to.image = "${image ?: rootProject.name}:${project.version}"
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