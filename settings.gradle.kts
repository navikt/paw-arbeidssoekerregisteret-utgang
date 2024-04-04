plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
    kotlin("jvm") version "1.9.20" apply false
}

include(
    "utgang-pdl",
    "utgang-formidlingsgruppe",
    "main-avro-schema-classes",
    "kafka-key-generator-client"
)

dependencyResolutionManagement {
    val githubPassword: String by settings
    repositories {
        maven {
            setUrl("https://maven.pkg.github.com/navikt/paw-observability")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
        mavenCentral()
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
    }
    versionCatalogs {
        val pawUtilsVersion = "24.02.06.10-1"
        val pawPdlClientVersion = "24.03.20.30-1"
        val pawAaregClientVersion = "24.01.12.16-1"
        val interneEventerVersion = "24.04.02.165-1"
        val arbeidssokerregisteretVersion = "1.8062260419.22-1"

        val orgApacheKafkaVersion = "3.6.0"
        val orgApacheAvroVersion = "1.11.3"
        val ioConfluentKafkaVersion = "7.6.0"
        val comFasterxmlJacksonVersion = "2.16.1"
        val comSksamuelHopliteVersion = "2.8.0.RC3"
        val noNavCommonVersion = "3.2024.02.21_11.18-8f9b43befae1"
        val noNavSecurityVersion = "3.1.5"
        val ktorVersion = "2.3.9"

        val logstashVersion = "7.3"
        val logbackVersion = "1.4.14"
        create("loggingLibs") {
            library(
                "logbackClassic",
                "ch.qos.logback",
                "logback-classic"
            ).version(logbackVersion)
            library(
                "logstashLogbackEncoder",
                "net.logstash.logback",
                "logstash-logback-encoder"
            ).version(logstashVersion)
        }
        create("ktorClient") {
            library(
                "contentNegotiation",
                "io.ktor",
                "ktor-client-content-negotiation"
            ).version(ktorVersion)
            library(
                "core",
                "io.ktor",
                "ktor-client-core"
            ).version(ktorVersion)
            library(
                "cio",
                "io.ktor",
                "ktor-client-cio"
            ).version(ktorVersion)
        }
        create("pawObservability") {
            from("no.nav.paw.observability:observability-version-catalog:24.03.05.11-1")
        }
        create("pawClients") {
            library(
                "pawPdlClient",
                "no.nav.paw",
                "pdl-client"
            ).version(pawPdlClientVersion)
            library(
                "pawAaregClient",
                "no.nav.paw",
                "aareg-client"
            ).version(pawAaregClientVersion)
        }
        create("arbeidssoekerRegisteret") {
            library(
                "internalEvents",
                "no.nav.paw.arbeidssokerregisteret.internt.schema",
                "interne-eventer"
            ).version(interneEventerVersion)
            library(
                "apiKotlin",
                "no.nav.paw.arbeidssokerregisteret.api.schema",
                "arbeidssoekerregisteret-kotlin"
            ).version(arbeidssokerregisteretVersion)
            library(
                "mainAvroSchema",
                "no.nav.paw.arbeidssokerregisteret.api",
                "main-avro-schema"
            ).version(arbeidssokerregisteretVersion)
        }
        create("pawUtils") {
            library(
                "kafka",
                "no.nav.paw.kafka",
                "kafka"
            ).version(pawUtilsVersion)
            library(
                "kafkaStreams",
                "no.nav.paw.kafka-streams",
                "kafka-streams"
            ).version(pawUtilsVersion)
            library(
                "hopliteConfig",
                "no.nav.paw.hoplite-config",
                "hoplite-config"
            ).version(pawUtilsVersion)
        }
        create("orgApacheKafka") {
            library(
                "kafkaClients",
                "org.apache.kafka",
                "kafka-clients"
            ).version(orgApacheKafkaVersion)
            library(
                "kafkaStreams",
                "org.apache.kafka",
                "kafka-streams"
            ).version(orgApacheKafkaVersion)
            library(
                "streamsTest",
                "org.apache.kafka",
                "kafka-streams-test-utils"
            ).version(orgApacheKafkaVersion)
        }
        create("apacheAvro") {
            library(
                "avro",
                "org.apache.avro",
                "avro"
            ).version(orgApacheAvroVersion)
            library(
                "kafkaAvroSerializer",
                "io.confluent",
                "kafka-avro-serializer"
            ).version(ioConfluentKafkaVersion)
            library(
                "kafkaStreamsAvroSerde",
                "io.confluent",
                "kafka-streams-avro-serde"
            ).version(ioConfluentKafkaVersion)
        }
        create("jacskon") {
            library(
                "jacksonDatatypeJsr310",
                "com.fasterxml.jackson.datatype",
                "jackson-datatype-jsr310"
            ).version(comFasterxmlJacksonVersion)
            library(
                "jacksonModuleKotlin",
                "com.fasterxml.jackson.module",
                "jackson-module-kotlin"
            ).version(comFasterxmlJacksonVersion)
            library(
                "ktorSerialization",
                "io.ktor",
                "ktor-serialization-jackson"
            ).version(ktorVersion)
            library(
                "ktorSerializationJvm",
                "io.ktor",
                "ktor-serialization-jackson-jvm"
            ).version(ktorVersion)
        }
        create("navCommon") {
            library(
                "tokenClient",
                "no.nav.common",
                "token-client"
            ).version(noNavCommonVersion)
            library(
                "log",
                "no.nav.common",
                "log"
            ).version(noNavCommonVersion)
        }
        create("navSecurity") {
            library(
                "tokenValidationKtorV2",
                "no.nav.security",
                "token-validation-ktor-v2"
            ).version(noNavSecurityVersion)
            library(
                "tokenClient",
                "no.nav.security",
                "token-client-core"
            ).version(noNavSecurityVersion)
        }
        create("hoplite") {
            library(
                "hopliteCore",
                "com.sksamuel.hoplite",
                "hoplite-core"
            ).version(comSksamuelHopliteVersion)
            library(
                "hopliteToml",
                "com.sksamuel.hoplite",
                "hoplite-toml"
            ).version(comSksamuelHopliteVersion)
            library(
                "hopliteYaml",
                "com.sksamuel.hoplite",
                "hoplite-yaml"
            ).version(comSksamuelHopliteVersion)
        }
    }
}
