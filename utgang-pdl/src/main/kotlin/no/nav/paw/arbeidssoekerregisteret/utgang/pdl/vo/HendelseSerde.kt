package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.vo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avvist
import no.nav.paw.arbeidssokerregisteret.intern.v1.AvvistStoppAvPeriode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import no.nav.paw.arbeidssokerregisteret.intern.v1.OpplysningerOmArbeidssoekerMottatt
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import no.nav.paw.arbeidssokerregisteret.intern.v1.avsluttetHendelseType
import no.nav.paw.arbeidssokerregisteret.intern.v1.avvistHendelseType
import no.nav.paw.arbeidssokerregisteret.intern.v1.avvistStoppAvPeriodeHendelseType
import no.nav.paw.arbeidssokerregisteret.intern.v1.opplysningerOmArbeidssoekerHendelseType
import no.nav.paw.arbeidssokerregisteret.intern.v1.startetHendelseType
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class HendelseSerde : Serde<Hendelse> {
    private val objectMapper = hendelseObjectMapper()
    override fun serializer() = HendelseSerializer(objectMapper)
    override fun deserializer() = HendelseDeserializer(objectMapper)
}

class HendelseSerializer(private val objectMapper: ObjectMapper): Serializer<Hendelse> {
    constructor(): this(hendelseObjectMapper())

    override fun serialize(topic: String?, data: Hendelse?): ByteArray {
        return data?.let {
            objectMapper.writeValueAsBytes(it)
        } ?: ByteArray(0)
    }
}

class HendelseDeserializer(private val objectMapper: ObjectMapper): Deserializer<Hendelse> {
    constructor(): this(hendelseObjectMapper())

    override fun deserialize(topic: String?, data: ByteArray?): Hendelse? {
        if (data == null) return null
        return deserialize(objectMapper, data)
    }
}

private fun hendelseObjectMapper(): ObjectMapper = ObjectMapper()
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

fun deserialize(objectMapper: ObjectMapper, json: ByteArray): Hendelse {
    val node = objectMapper.readTree(json)
    return when (val hendelseType = node.get("hendelseType")?.asText()) {
        startetHendelseType -> objectMapper.readValue<Startet>(node.traverse())
        avsluttetHendelseType -> objectMapper.readValue<Avsluttet>(node.traverse())
        avvistHendelseType -> objectMapper.readValue<Avvist>(node.traverse())
        avvistStoppAvPeriodeHendelseType -> objectMapper.readValue<AvvistStoppAvPeriode>(node.traverse())
        opplysningerOmArbeidssoekerHendelseType -> objectMapper.readValue<OpplysningerOmArbeidssoekerMottatt>(node.traverse())
        else -> throw IllegalArgumentException("Ukjent hendelse type: '$hendelseType'")
    }
}