package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.IdAndRecordKey
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.vo.HendelseSerde
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.kafkakeygenerator.client.inMemoryKafkaKeysMock
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Folkeregisterpersonstatus
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.HentPersonBolkResult
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Person
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Time
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.internals.InMemoryKeyValueBytesStoreSupplier
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder
import java.util.*

data class TestScope(
    val periodeTopic: TestInputTopic<Long, Periode>,
    val hendelseloggTopic: TestOutputTopic<Long, Avsluttet>,
    val kevValueStore: KeyValueStore<Long, Periode>,
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

    val pdlMockResponse1 = generatePdlMockResponse("12345678901", "doedIFolkeregisteret")
    val pdlMockResponse2 = generatePdlMockResponse("12345678902", "bosattEtterFolkeregisterloven")

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            stateStoreName = stateStoreName,
            hendelseLoggTopic = hendelsesLogTopic,
            periodeTopic = periodeTopic,
            idAndRecordKeyFunction = idAndRecordKeyFunction,
            pdlHentForenkletStatus = { idents, _, _ ->
                if (idents.contains("12345678901"))
                    pdlMockResponse1
                else
                    pdlMockResponse2
            },
            prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        ),
        kafkaStreamProperties
    )
    val periodeInputTopic = testDriver.createInputTopic(
        periodeTopic,
        Serdes.Long().serializer(),
        periodeSerde.serializer()
    )
    val hendelseOutputTopic = testDriver.createOutputTopic(
        hendelsesLogTopic,
        Serdes.Long().deserializer(),
        hendelseSerde.deserializer()
    )
    return TestScope(
        periodeTopic = periodeInputTopic,
        hendelseloggTopic = hendelseOutputTopic,
        kevValueStore = testDriver.getKeyValueStore(stateStoreName),
        topologyTestDriver = testDriver
    )
}

fun generatePdlMockResponse(ident: String, forenkletStatus: String): List<HentPersonBolkResult> {
    return listOf(HentPersonBolkResult(
            ident,
            person = Person(
                listOf(
                    Folkeregisterpersonstatus(
                        forenkletStatus,
                    )
                ),
            ),
            code = "ok",
        ))

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


