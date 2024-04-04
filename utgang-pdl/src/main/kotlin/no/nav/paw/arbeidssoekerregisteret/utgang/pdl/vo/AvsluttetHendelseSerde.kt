package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.vo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class AvsluttetHendelseSerde : Serde<Avsluttet> {
    override fun serializer() = AvsluttetHendelseSerializer()

    override fun deserializer(): Deserializer<Avsluttet> = AvsluttetHendelseSerializerDeserializer()
}

class AvsluttetHendelseSerializer : Serializer<Avsluttet> {
    override fun serialize(topic: String?, data: Avsluttet?): ByteArray {
        return avsluttetHendelseObjectMapper.writeValueAsBytes(data)
    }
}

class AvsluttetHendelseSerializerDeserializer : Deserializer<Avsluttet> {
    override fun deserialize(topic: String?, data: ByteArray?): Avsluttet? {
        return avsluttetHendelseObjectMapper.readValue(data, Avsluttet::class.java)
    }
}


private val avsluttetHendelseObjectMapper: ObjectMapper = ObjectMapper()
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