package no.naw.arbeidssoekerregisteret.utgang.pdl

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.config.kafka.KAFKA_CONFIG_WITH_SCHEME_REG
import no.nav.paw.config.kafka.KafkaConfig
import no.nav.paw.config.kafka.streams.KafkaStreamsFactory
import no.naw.arbeidssoekerregisteret.utgang.pdl.clients.createIdAndRecordKeyFunction
import no.naw.arbeidssoekerregisteret.utgang.pdl.config.APPLICATION_CONFIG_FILE
import no.naw.arbeidssoekerregisteret.utgang.pdl.config.ApplicationConfiguration
import no.naw.arbeidssoekerregisteret.utgang.pdl.helse.Helse
import no.naw.arbeidssoekerregisteret.utgang.pdl.helse.initKtor
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory

const val periodeTopic = "paw.arbeidssokerperioder-beta-v14"
const val hendelsesLogTopic = "paw.arbeidssoker-hendelseslogg-beta-v14"

fun main() {
    val logger = LoggerFactory.getLogger("app")
    logger.info("Starter: {}", ApplicationInfo.id)
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val kafkaConfig = loadNaisOrLocalConfiguration<KafkaConfig>(KAFKA_CONFIG_WITH_SCHEME_REG)
    val applicationConfiguration = loadNaisOrLocalConfiguration<ApplicationConfiguration>(APPLICATION_CONFIG_FILE)

    val idAndRecordKeyFunction = createIdAndRecordKeyFunction()
    val streamsConfig = KafkaStreamsFactory(applicationConfiguration.applicationIdSuffix, kafkaConfig)
        .withDefaultKeySerde(Serdes.LongSerde::class)
        .withDefaultValueSerde(SpecificAvroSerde::class)

    val streamsBuilder = StreamsBuilder()
        .addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(applicationConfiguration.aktivePerioderStateStoreName),
                Serdes.Long(),
                streamsConfig.createSpecificAvroSerde()
            )
        )
    val topology = streamsBuilder.appTopology(
        prometheusMeterRegistry,
        "aktivePerioder",
        idAndRecordKeyFunction,
        periodeTopic,
        hendelsesLogTopic
    )
    val kafkaStreams = KafkaStreams(
        topology,
        StreamsConfig(streamsConfig.properties)
    )
    kafkaStreams.setUncaughtExceptionHandler { throwable ->
        logger.error("Uventet feil: ${throwable.message}", throwable)
        StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION
    }
    initKtor(
        kafkaStreamsMetrics = KafkaStreamsMetrics(kafkaStreams),
        prometheusRegistry = prometheusMeterRegistry,
        helse = Helse(kafkaStreams)
    ).start(wait = true)
    logger.info("Avsluttet")
}

