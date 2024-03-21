package no.nav.paw.arbeidssoekerregisteret.app.vo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory

class ArenaFormidlingsgruppeSerde: Serde<ArenaFormidlingsruppe> {
    override fun serializer() = ArenaFormidlingsgruppeSerializer()

    override fun deserializer(): Deserializer<ArenaFormidlingsruppe> = ArenaFormidlingsgruppeDeserializer()
}

class ArenaFormidlingsgruppeSerializer() : Serializer<ArenaFormidlingsruppe> {
    override fun serialize(topic: String?, data: ArenaFormidlingsruppe?): ByteArray {
        return hendelseObjectMapper.writeValueAsBytes(data)
    }
}

class ArenaFormidlingsgruppeDeserializer(): Deserializer<ArenaFormidlingsruppe> {
    override fun deserialize(topic: String?, data: ByteArray?): ArenaFormidlingsruppe? {
        if (data == null) return null
        return hendelseObjectMapper.readValue(data)
    }

}

private val hendelseObjectMapper: ObjectMapper = ObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    .registerModules(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false)
            .configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build(),
        JavaTimeModule()
    )
