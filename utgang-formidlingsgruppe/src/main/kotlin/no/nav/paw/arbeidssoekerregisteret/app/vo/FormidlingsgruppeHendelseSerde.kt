package no.nav.paw.arbeidssoekerregisteret.app.vo

import com.fasterxml.jackson.core.type.TypeReference
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
import org.slf4j.LoggerFactory

class FormidlingsgruppeHendelseSerde: Serde<FormidlingsgruppeHendelse> {
    override fun serializer() = FormidlingsgruppeHendelseSerializer()

    override fun deserializer(): Deserializer<FormidlingsgruppeHendelse> = FormidlingsgruppeHendelseDeserializer()
}

class FormidlingsgruppeHendelseSerializer() : Serializer<FormidlingsgruppeHendelse> {
    override fun serialize(topic: String?, data: FormidlingsgruppeHendelse?): ByteArray {
        return hendelseObjectMapper.writeValueAsBytes(data)
    }
}

class FormidlingsgruppeHendelseDeserializer(): Deserializer<FormidlingsgruppeHendelse> {
    override fun deserialize(topic: String?, data: ByteArray?): FormidlingsgruppeHendelse? {
        if (data == null) return null
        val map = hendelseObjectMapper.readTree(data)
        LoggerFactory.getLogger("FormidlingsgruppeHendelseDeserializer")
            .info("Mottatt hendelse: ${map.fieldNames().asSequence().toList()}")
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