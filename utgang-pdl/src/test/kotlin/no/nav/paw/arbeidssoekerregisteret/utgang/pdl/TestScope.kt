package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.appTopology
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseSerde
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseState
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseStateSerde
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.HentPersonBolkResult
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
    val hendelseloggInputTopic: TestInputTopic<Long, Hendelse>,
    val hendelseloggOutputTopic: TestOutputTopic<Long, Hendelse>,
    val hendelseKeyValueStore: KeyValueStore<UUID, HendelseState>,
    val topologyTestDriver: TopologyTestDriver
)

fun testScope(pdlMockResponse: List<HentPersonBolkResult>): TestScope {
    val applicationConfig = loadNaisOrLocalConfiguration<ApplicationConfiguration>(
        APPLICATION_CONFIG_FILE
    )

    val periodeSerde = createAvroSerde<Periode>()

    val hendelseStateStoreName = applicationConfig.hendelseStateStoreName

    val streamBuilder = StreamsBuilder()
        .addStateStore(
            KeyValueStoreBuilder(
                InMemoryKeyValueBytesStoreSupplier(hendelseStateStoreName),
                Serdes.UUID(),
                HendelseStateSerde(),
                Time.SYSTEM
            )
        )

    val testDriver = TopologyTestDriver(
        streamBuilder.appTopology(
            hendelseStateStoreName = hendelseStateStoreName,
            periodeTopic = applicationConfig.periodeTopic,
            hendelseLoggTopic = applicationConfig.hendelseloggTopic,
            pdlHentForenkletStatus = { _, _, _ ->
                pdlMockResponse
            },
            prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        ),
        kafkaStreamProperties
    )
    val periodeInputTopic = testDriver.createInputTopic(
        applicationConfig.periodeTopic,
        Serdes.Long().serializer(),
        periodeSerde.serializer()
    )
    val hendelseInputTopic = testDriver.createInputTopic(
        applicationConfig.hendelseloggTopic,
        Serdes.Long().serializer(),
        HendelseSerde().serializer()
    )
    val hendelseOutputTopic = testDriver.createOutputTopic(
        applicationConfig.hendelseloggTopic,
        Serdes.Long().deserializer(),
        HendelseSerde().deserializer()
    )
    return TestScope(
        periodeTopic = periodeInputTopic,
        hendelseloggInputTopic = hendelseInputTopic,
        hendelseloggOutputTopic = hendelseOutputTopic,
        hendelseKeyValueStore = testDriver.getKeyValueStore(hendelseStateStoreName),
        topologyTestDriver = testDriver
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


