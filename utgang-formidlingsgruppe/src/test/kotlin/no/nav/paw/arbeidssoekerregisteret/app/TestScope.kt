package no.nav.paw.arbeidssoekerregisteret.app

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssoekerregisteret.app.vo.ArenaFormidlingsgruppeSerde
import no.nav.paw.arbeidssoekerregisteret.app.vo.ArenaFormidlingsruppe
import no.nav.paw.arbeidssoekerregisteret.app.vo.AvsluttetSerde
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.kafkakeygenerator.auth.NaisEnv
import no.nav.paw.kafkakeygenerator.client.inMemoryKafkaKeysMock
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Time
import org.apache.kafka.streams.*
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.internals.InMemoryKeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder
import java.time.Period
import java.util.*


data class TestScope(
    val periodeTopic: TestInputTopic<Long, Periode>,
    val formidlingsgruppeTopic: TestInputTopic<String, ArenaFormidlingsruppe>,
    val hendelseloggTopic: TestOutputTopic<Long, Avsluttet>,
    val kevValueStore: KeyValueStore<Long, Period>,
    val topologyTestDriver: TopologyTestDriver,
    val kafkaKeysClient: KafkaIdAndRecordKeyFunction
)

fun testScope(): TestScope {
    val idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction = with(inMemoryKafkaKeysMock()) {
        KafkaIdAndRecordKeyFunction { identitetsnummer ->
            runBlocking { getIdAndKey(identitetsnummer) }
                ?.let {
                    IdAndRecordKey(
                        id = it.id,
                        recordKey = it.key
                    )
                }
        }
    }
    val periodeSerde = createAvroSerde<Periode>()
    val formidlingsgruppeSerde = ArenaFormidlingsgruppeSerde()
    val avsluttetSerde = AvsluttetSerde()
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

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            stateStoreName = stateStoreName,
            hendelseLoggTopic = hendelsesLogTopic,
            periodeTopic = periodeTopic,
            formidlingsgrupperTopic = formidlingsGruppeTopic(NaisEnv.Local),
            idAndRecordKeyFunction = idAndRecordKeyFunction,
            prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        ),
        kafkaStreamProperties
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
        avsluttetSerde.deserializer()
    )
    return TestScope(
        periodeTopic = periodeInputTopic,
        formidlingsgruppeTopic = formidlingsgruppeInputTopic,
        hendelseloggTopic = hendelseOutputTopic,
        kevValueStore = testDriver.getKeyValueStore(stateStoreName),
        topologyTestDriver = testDriver,
        kafkaKeysClient = idAndRecordKeyFunction
    )
}

const val SCHEMA_REGISTRY_SCOPE = "mock"
inline fun <reified T : SpecificRecord> createAvroSerde(): Serde<T> {

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

val kafkaStreamProperties = Properties().apply {
    this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
    this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
    this[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.Long().javaClass
    this[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde<SpecificRecord>().javaClass
    this[KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS] = "true"
    this[KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = "mock://$SCHEMA_REGISTRY_SCOPE"
}