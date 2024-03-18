package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssoekerregisteret.app.vo.HendelseSerde
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.IdAndRecordKey
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.config.kafka.KAFKA_CONFIG_WITH_SCHEME_REG
import no.nav.paw.config.kafka.KafkaConfig
import no.nav.paw.config.kafka.streams.KafkaStreamsFactory
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
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssokerregisteret.intern.v1.HarIdentitetsnummer
import no.nav.paw.pdl.PdlClient
import no.nav.paw.pdl.graphql.generated.hentperson.Person

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
    val pdlClientMock: PdlClient =
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
    val kafkaConfig: KafkaConfig = loadNaisOrLocalConfiguration(KAFKA_CONFIG_WITH_SCHEME_REG)
    val kafkaStreamsFactory = KafkaStreamsFactory("test", kafkaConfig)
        .withDefaultKeySerde(Serdes.Long()::class)
        .withDefaultValueSerde(SpecificAvroSerde::class)

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            stateStoreName = stateStoreName,
            hendelseLoggTopic = hendelsesLogTopic,
            periodeTopic = periodeTopic,
            idAndRecordKeyFunction = idAndRecordKeyFunction,
            pdlClient = pdlClientMock,
            prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        ),
        kafkaStreamsFactory.properties
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

class MockPdlClient(): PdlClient {
    fun PdlClient.hentPerson(identitetsnummer: String): Person {
        return Person(
            folkeregisterpersonstatus = listOf("bosattEtterFolkeregisterloven")
        )
    }
}
