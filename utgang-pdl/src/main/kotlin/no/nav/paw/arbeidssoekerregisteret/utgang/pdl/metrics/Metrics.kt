package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.metrics

import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_UTGANG_PDL = "paw_arbeidssoekerregisteret_utgang_pdl"

const val AVSLUTTET_HENDELSE = "_avsluttet_hendelser"
const val AVSLUTTET_HENDELSE_AARSAK = "avsluttet_hendelse_aarsak"

const val PDL_HENT_PERSON = "_hent_person"
const val PDL_HENT_PERSON_STATUS = "hent_person_status"

fun PrometheusMeterRegistry.tellPdlAvsluttetHendelser(aarsak: String) = counter(
    METRICS_UTGANG_PDL + AVSLUTTET_HENDELSE,
    listOf(Tag.of(AVSLUTTET_HENDELSE_AARSAK, aarsak))
).increment()

fun PrometheusMeterRegistry.tellStatusFraPdlHentPersonBolk(status: String) = counter(
    METRICS_UTGANG_PDL + PDL_HENT_PERSON,
    listOf(Tag.of(PDL_HENT_PERSON_STATUS, status))
).increment()



