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
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
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
    val hendelseLoggInputTopic: TestInputTopic<Long, Hendelse>,
    val hendelseloggOutputTopic: TestOutputTopic<Long, Hendelse>,
    val periodeKeyValueStore: KeyValueStore<Long, Periode>,
    val hendelseKeyValueStore: KeyValueStore<Long, Startet>,
    val topologyTestDriver: TopologyTestDriver
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
                } ?: throw Exception("Kunne ikke hente kafka key (404 not found svar fra kafka key generator)")
        }
    }

    val (periodeTopic, hendelsesLoggTopic) = loadNaisOrLocalConfiguration<ApplicationConfiguration>(
        APPLICATION_CONFIG_FILE
    )

    val periodeSerde = createAvroSerde<Periode>()
    val hendelseSerde = HendelseSerde()

    val periodeStateStoreName = "periodeStateStore"
    val hendelseStateStoreName = "hendelseStateStore"

    val streamBuilder = StreamsBuilder()
        .addStateStore(
            KeyValueStoreBuilder(
                InMemoryKeyValueBytesStoreSupplier(periodeStateStoreName),
                Serdes.Long(),
                periodeSerde,
                Time.SYSTEM
            )
        )
        .addStateStore(
            KeyValueStoreBuilder(
                InMemoryKeyValueBytesStoreSupplier(hendelseStateStoreName),
                Serdes.Long(),
                hendelseSerde,
                Time.SYSTEM
            )
        )

    val pdlMockResponseDoed = generatePdlMockResponse("12345678901", "doedIFolkeregisteret")
    val pdlMockResponseBosattEtterFolkeregisterloven =
        generatePdlMockResponse("12345678902", "bosattEtterFolkeregisterloven")
    val pdlMockResponseNotFound = generatePdlMockResponse("12345678903", "doedIFolkeregisteret", "not_found")
    val pdlMockResponseIkkeBosatt = generatePdlMockResponse("12345678904", "ikkeBosatt")
    val pdlMockResponseBadRequest = generatePdlMockResponse("", "", "bad_request")

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            periodeStateStoreName = periodeStateStoreName,
            hendelseStateStoreName = hendelseStateStoreName,
            periodeTopic = periodeTopic,
            hendelseLoggTopic = hendelsesLoggTopic,
            idAndRecordKeyFunction = idAndRecordKeyFunction,
            pdlHentForenkletStatus = { idents, _, _ ->
                when (idents.first()) {
                    "12345678901" -> pdlMockResponseDoed
                    "12345678902" -> pdlMockResponseBosattEtterFolkeregisterloven
                    "12345678903" -> pdlMockResponseNotFound
                    "12345678904" -> pdlMockResponseIkkeBosatt
                    else -> pdlMockResponseBadRequest
                }
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
    val hendelseInputTopic = testDriver.createInputTopic(
        hendelsesLoggTopic,
        Serdes.Long().serializer(),
        hendelseSerde.serializer()
    )
    val hendelseOutputTopic = testDriver.createOutputTopic(
        hendelsesLoggTopic,
        Serdes.Long().deserializer(),
        hendelseSerde.deserializer()
    )
    return TestScope(
        periodeTopic = periodeInputTopic,
        hendelseLoggInputTopic = hendelseInputTopic,
        hendelseloggOutputTopic = hendelseOutputTopic,
        periodeKeyValueStore = testDriver.getKeyValueStore(periodeStateStoreName),
        hendelseKeyValueStore = testDriver.getKeyValueStore(hendelseStateStoreName),
        topologyTestDriver = testDriver
    )
}

fun generatePdlMockResponse(ident: String, forenkletStatus: String, status: String = "ok") = listOf(
    HentPersonBolkResult(
        ident,
        person = Person(
            listOf(
                Folkeregisterpersonstatus(
                    forenkletStatus,
                )
            ),
        ),
        code = status,
    )
)

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


