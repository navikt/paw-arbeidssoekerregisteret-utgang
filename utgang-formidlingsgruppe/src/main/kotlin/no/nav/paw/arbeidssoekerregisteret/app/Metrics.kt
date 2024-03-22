package no.nav.paw.arbeidssoekerregisteret.app

import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.functions.FilterResultat
import no.nav.paw.arbeidssoekerregisteret.app.vo.Formidlingsgruppe

fun PrometheusMeterRegistry.tellFilterResultat(result: FilterResultat) {
    counter(
        "paw_arbeidssoekerregisteret_formidlingsgrupper_filter",
        listOf(Tag.of("resultat", result.name))
    ).increment()
}

fun PrometheusMeterRegistry.tellUgyldigHendelse() {
    counter(
        "paw_arbeidssoekerregisteret_formidlingsgrupper_filter",
        listOf(Tag.of("resultat", "invalid"))
    ).increment()
}

fun PrometheusMeterRegistry.tellIkkeIPDL() {
    counter(
        "paw_arbeidssoekerregisteret_formidlingsgrupper_filter",
        listOf(Tag.of("resultat", "not_in_pdl"))
    ).increment()
}

fun PrometheusMeterRegistry.tellIgnorertGrunnetFormidlingsgruppe(formidlingsgruppe: Formidlingsgruppe) {
    counter(
        "paw_arbeidssoekerregisteret_formidlingsgrupper_filter",
        listOf(Tag.of("resultat", formidlingsgruppe.kode))
    ).increment()
}
