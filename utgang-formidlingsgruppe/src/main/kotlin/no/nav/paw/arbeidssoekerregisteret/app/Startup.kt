package no.nav.paw.arbeidssoekerregisteret.app

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.vo.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.app.helse.Helse
import no.nav.paw.arbeidssokerregisteret.app.helse.initKtor
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.config.kafka.KAFKA_CONFIG_WITH_SCHEME_REG
import no.nav.paw.config.kafka.KafkaConfig
import no.nav.paw.config.kafka.streams.KafkaStreamsFactory
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import no.nav.paw.kafkakeygenerator.auth.NaisEnv
import no.nav.paw.kafkakeygenerator.auth.currentNaisEnv
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Repartitioned
import java.time.Instant
import java.util.*

const val partitionCount: Int = 6

const val periodeTopic = "paw.arbeidssokerperioder-beta-v14"
const val hendelsesLogTopic = "paw.formidlingsgruppetopic-beta-v1"
fun formidlingsGruppeTopic(env: NaisEnv) = "teamarenanais.aapen-arena-formidlingsgruppeendret-v1-${if (env == NaisEnv.ProdGCP) "p" else "q"}"

fun main() {
    val logger = LoggerFactory.getLogger("app")
    logger.info("Starter: {}", ApplicationInfo.id)
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val kafkaConfig = loadNaisOrLocalConfiguration<KafkaConfig>(KAFKA_CONFIG_WITH_SCHEME_REG)
    logger.info("Lastet kafka config")
    val idAndRecordKeyFunction = createIdAndRecordKeyFunction()
    logger.info("idAndRecordKeyFunction opprettet")
    val streamsConfig = KafkaStreamsFactory("v1", kafkaConfig)
        .withDefaultKeySerde(Serdes.LongSerde::class)
        .withDefaultValueSerde(SpecificAvroSerde::class)
    logger.info("streamsConfig opprettet")
    val streamsBuilder = StreamsBuilder()
        .addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("aktivePerioder"),
                Serdes.Long(),
                streamsConfig.createSpecificAvroSerde()
            )
        )
    logger.info("streamsBuilder opprettet")
    val topology = streamsBuilder.appTopology(
        prometheusMeterRegistry,
        "aktivePerioder",
        idAndRecordKeyFunction,
        periodeTopic,
        formidlingsGruppeTopic(currentNaisEnv),
        hendelsesLogTopic
    )
    logger.info("topology opprettet")
    val kafkaStreams = KafkaStreams(
        topology,
        StreamsConfig(streamsConfig.properties)
    )
    logger.info("kafkaStreams opprettet")
    kafkaStreams.setUncaughtExceptionHandler { throwable ->
        logger.error("Uventet feil: ${throwable.message}", throwable)
        StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION
    }
    kafkaStreams.start()
    logger.info("UncaughtExceptionHandler satt")
    val helse = Helse(kafkaStreams)
    val streamMetrics = KafkaStreamsMetrics(kafkaStreams)
    logger.info("Starter ktor...")
    initKtor(
        kafkaStreamsMetrics = streamMetrics,
        prometheusRegistry = prometheusMeterRegistry,
        helse = helse
    ).start(wait = true)
    logger.info("Avsluttet")
}

