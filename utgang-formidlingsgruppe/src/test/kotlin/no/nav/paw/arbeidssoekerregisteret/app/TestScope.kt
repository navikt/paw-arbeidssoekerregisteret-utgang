package no.nav.paw.arbeidssoekerregisteret.app

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssoekerregisteret.app.vo.FormidlingsgruppeHendelse
import no.nav.paw.arbeidssoekerregisteret.app.vo.FormidlingsgruppeHendelseSerde
import no.nav.paw.arbeidssoekerregisteret.app.vo.HendelseSerde
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.config.kafka.KAFKA_CONFIG_WITH_SCHEME_REG
import no.nav.paw.config.kafka.KafkaConfig
import no.nav.paw.config.kafka.streams.KafkaStreamsFactory
import no.nav.paw.kafkakeygenerator.auth.NaisEnv
import no.nav.paw.kafkakeygenerator.client.inMemoryKafkaKeysMock
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Time
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.internals.InMemoryKeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder
import java.time.Period


data class TestScope(
    val periodeTopic: TestInputTopic<Long, Periode>,
    val formidlingsgruppeTopic: TestInputTopic<String, FormidlingsgruppeHendelse>,
    val hendelseloggTopic: TestOutputTopic<Long, Avsluttet>,
    val kevValueStore: KeyValueStore<Long, Period>,
    val topologyTestDriver: TopologyTestDriver
)

fun testScope(): TestScope {
    val idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction = with(inMemoryKafkaKeysMock()) {
        KafkaIdAndRecordKeyFunction { identitetsnummer ->
            runBlocking { getIdAndKey(identitetsnummer) }
                .let {
                    IdAndRecordKey(
                        id = it.id,
                        recordKey = it.key
                    )
                }
        }
    }
    val periodeSerde = createAvroSerde<Periode>()
    val formidlingsgruppeSerde = FormidlingsgruppeHendelseSerde()
    val hendelseSerde = HendelseSerde()
    val stateStoreName = "stateStore"
    val streamBuilder = StreamsBuilder()
        .addStateStore(
            KeyValueStoreBuilder(
                InMemoryKeyValueBytesStoreSupplier(stateStoreName),
                Serdes.Long(),
                periodeSerde,
                Time.SYSTEM
            )
        )
    val kafkaConfig: KafkaConfig = loadNaisOrLocalConfiguration(KAFKA_CONFIG_WITH_SCHEME_REG)
    val kafkaStreamsFactory = KafkaStreamsFactory("test", kafkaConfig)
        .withDefaultKeySerde(Serdes.Long()::class)
        .withDefaultValueSerde(SpecificAvroSerde::class)

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            stateStoreName = stateStoreName,
            hendelseLoggTopic = hendelsesLogTopic,
            periodeTopic = periodeTopic,
            formidlingsgrupperTopic = formidlingsGruppeTopic(NaisEnv.Local),
            idAndRecordKeyFunction = idAndRecordKeyFunction,
            prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        ),
        kafkaStreamsFactory.properties
    )
    val periodeInputTopic = testDriver.createInputTopic(
        periodeTopic,
        Serdes.Long().serializer(),
        periodeSerde.serializer()
    )
    val formidlingsgruppeInputTopic = testDriver.createInputTopic(
        formidlingsGruppeTopic(NaisEnv.Local),
        Serdes.String().serializer(),
        formidlingsgruppeSerde.serializer(),
    )
    val hendelseOutputTopic = testDriver.createOutputTopic(
        hendelsesLogTopic,
        Serdes.Long().deserializer(),
        hendelseSerde.deserializer()
    )
    return TestScope(
        periodeTopic = periodeInputTopic,
        formidlingsgruppeTopic = formidlingsgruppeInputTopic,
        hendelseloggTopic = hendelseOutputTopic,
        kevValueStore = testDriver.getKeyValueStore(stateStoreName),
        topologyTestDriver = testDriver
    )
}

inline fun <reified T : SpecificRecord> createAvroSerde(): Serde<T> {
    val SCHEMA_REGISTRY_SCOPE = "mock"
    return SpecificAvroSerde<T>(MockSchemaRegistry.getClientForScope(SCHEMA_REGISTRY_SCOPE)).apply {
        configure(
            mapOf(
                KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS to "true",
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://$SCHEMA_REGISTRY_SCOPE"
            ),
            false
        )
    }
}