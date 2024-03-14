package no.nav.paw.arbeidssoekerregisteret.app

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.vo.FormidlingsgruppeHendelseSerde
import no.nav.paw.arbeidssoekerregisteret.app.vo.lagreEllerSlettPeriode
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
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


fun main() {
    val logger = LoggerFactory.getLogger("app")
    logger.info("Starter: {}", ApplicationInfo.id)
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val kafkaConfig = loadNaisOrLocalConfiguration<KafkaConfig>(KAFKA_CONFIG_WITH_SCHEME_REG)
    val idAndRecordKeyFunction = createIdAndRecordKeyFunction()
    val streamsConfig = KafkaStreamsFactory("v1", kafkaConfig)
        .withDefaultKeySerde(Serdes.LongSerde::class)
        .withDefaultValueSerde(SpecificAvroSerde::class)

    val streamsBuilder = StreamsBuilder()
        .addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("aktivePerioder"),
                Serdes.UUID(),
                streamsConfig.createSpecificAvroSerde()
            )
        )


}

fun topology(
    streamsBuilder: StreamsBuilder,
    prometheusRegistry: PrometheusMeterRegistry,
    stateStoreName: String,
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    periodeTopic: String,
    formidlingsgrupperTopic: String
) {
    streamsBuilder.stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            stateStoreName = stateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction
        )

    streamsBuilder
        .stream(periodeTopic, Consumed.with(Serdes.String(), FormidlingsgruppeHendelseSerde()))
        .filter { _, value ->
            value.formidlingsgruppe.kode.equals("ISERV", ignoreCase = true)
        }
        .map { key, value ->
            val (id, newKey) = idAndRecordKeyFunction(value.foedselsnummer.foedselsnummer)
            KeyValue(newKey, value.copy(idFraKafkaKeyGenerator = id))
        }
        .repartition()



}