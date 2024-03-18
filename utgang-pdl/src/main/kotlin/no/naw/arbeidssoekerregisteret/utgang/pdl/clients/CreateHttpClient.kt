package no.naw.arbeidssoekerregisteret.utgang.pdl.clients

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson

fun createHttpClient() = HttpClient() {
    install(ContentNegotiation) {
        jackson()
    }
}