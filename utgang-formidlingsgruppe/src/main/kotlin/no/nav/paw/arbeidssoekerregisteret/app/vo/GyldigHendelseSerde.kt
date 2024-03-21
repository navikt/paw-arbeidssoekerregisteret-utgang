package no.nav.paw.arbeidssoekerregisteret.app.vo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class GyldigHendelseSerde: Serde<GyldigHendelse> {
    override fun serializer() = GyldigHendelseSerializer()

    override fun deserializer(): Deserializer<GyldigHendelse> = GyldigHendelseDeserializer()
}

class GyldigHendelseSerializer() : Serializer<GyldigHendelse> {
    override fun serialize(topic: String?, data: GyldigHendelse?): ByteArray {
        return hendelseObjectMapper.writeValueAsBytes(data)
    }
}

class GyldigHendelseDeserializer(): Deserializer<GyldigHendelse> {
    override fun deserialize(topic: String?, data: ByteArray?): GyldigHendelse? {
        if (data == null) return null
        return hendelseObjectMapper.readValue(data)
    }

}


private val hendelseObjectMapper: ObjectMapper = ObjectMapper()
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