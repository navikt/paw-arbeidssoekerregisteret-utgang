package no.nav.paw.arbeidssoekerregisteret.app.vo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class AvsluttetSerde: Serde<Avsluttet> {
    override fun serializer() = HendelseSerializer()

    override fun deserializer(): Deserializer<Avsluttet> = HendelseSerializerDeserializer()
}

class HendelseSerializer() : Serializer<Avsluttet> {
    override fun serialize(topic: String?, data: Avsluttet?): ByteArray {
        return hendelseObjectMapper.writeValueAsBytes(data)
    }
}

class HendelseSerializerDeserializer(): Deserializer<Avsluttet> {
    override fun deserialize(topic: String?, data: ByteArray?): Avsluttet? {
        return hendelseObjectMapper.readValue(data, Avsluttet::class.java)
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