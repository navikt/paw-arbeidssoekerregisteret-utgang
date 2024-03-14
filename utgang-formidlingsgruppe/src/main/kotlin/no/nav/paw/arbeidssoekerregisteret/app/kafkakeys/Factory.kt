package no.nav.paw.arbeidssoekerregisteret.app.kafkakeys

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.jackson
import no.nav.paw.arbeidssokerregisteret.config.KafkaKeysConfig
import no.nav.paw.migrering.app.kafkakeys.KafkaKeysClient
import no.nav.paw.migrering.app.kafkakeys.StandardKafkaKeysClient
import no.nav.paw.migrering.app.kafkakeys.inMemoryKafkaKeysMock


fun kafkaKeysKlient(konfigurasjon: KafkaKeyConfig, m2mTokenFactory: () -> String): KafkaKeysClient =
    when (konfigurasjon.url) {
        "MOCK" -> inMemoryKafkaKeysMock()
        else -> kafkaKeysMedHttpClient(konfigurasjon, m2mTokenFactory)
    }

private fun kafkaKeysMedHttpClient(config: KafkaKeyConfig, m2mTokenFactory: () -> String): KafkaKeysClient {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            jackson()
        }
    }
    return StandardKafkaKeysClient(
        httpClient,
        config.url
    ) { m2mTokenFactory() }
}
