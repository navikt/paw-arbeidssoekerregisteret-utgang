package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Opplysning
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import java.util.*

data class HendelseState(
    val brukerId: Long? = null,
    val periodeId: UUID,
    val recordKey: Long,
    val identitetsnummer: String,
    val opplysninger: Set<Opplysning>,
    var harTilhoerendePeriode: Boolean = false
)

class HendelseStateSerde : Serde<HendelseState> {
    override fun serializer() = HendelseStateSerializer()

    override fun deserializer(): Deserializer<HendelseState> = HendelseStateDeserializer()
}

class HendelseStateSerializer : Serializer<HendelseState> {
    override fun serialize(topic: String?, data: HendelseState?): ByteArray {
        return hendelseStateObjectMapper.writeValueAsBytes(data)
    }
}

class HendelseStateDeserializer : Deserializer<HendelseState> {
    override fun deserialize(topic: String?, data: ByteArray?): HendelseState? {
        return hendelseStateObjectMapper.readValue(data, HendelseState::class.java)
    }
}

private val hendelseStateObjectMapper: ObjectMapper = ObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModules(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, true)
            .configure(KotlinFeature.NullToEmptyMap, true)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build(),
        JavaTimeModule()
    )