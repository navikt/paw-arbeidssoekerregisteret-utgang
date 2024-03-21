package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.metrics

import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_UTGANG_PDL = "paw.arbeidssoekerregisteret.utgang.pdl"

const val ANTALL_AVSLUTTET_HENDELSER = "antall_avsluttet_hendelser"
const val AVSLUTTET_HENDELSE_AARSAK = "avsluttet_hendelse_aarsak"
const val STATUS_KODE = "status_kode"

fun PrometheusMeterRegistry.tellAvsluttetHendelser(aarsak: String) = counter(
    METRICS_UTGANG_PDL,
    Tags.of(Tag.of(ANTALL_AVSLUTTET_HENDELSER, "avsluttet hendelse"), Tag.of(AVSLUTTET_HENDELSE_AARSAK, aarsak))
).increment()

fun PrometheusMeterRegistry.tellStatusKoderFraPdlHentPersonBolk(status: String) = counter(
    METRICS_UTGANG_PDL,
    Tags.of(Tag.of(STATUS_KODE, status))
).increment()



