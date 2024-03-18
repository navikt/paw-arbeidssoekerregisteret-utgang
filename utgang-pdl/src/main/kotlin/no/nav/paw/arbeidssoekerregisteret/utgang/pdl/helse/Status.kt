package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.helse

import io.ktor.http.HttpStatusCode

data class Status(
    val code: HttpStatusCode,
    val message: String
)