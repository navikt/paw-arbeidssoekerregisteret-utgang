package no.nav.paw.arbeidssokerregisteret.app.helse

import io.ktor.http.*

data class Status(
    val code: HttpStatusCode,
    val message: String
)