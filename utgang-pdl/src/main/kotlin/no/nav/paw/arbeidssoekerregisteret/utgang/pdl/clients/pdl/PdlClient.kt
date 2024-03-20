package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.kafkakeygenerator.auth.AzureM2MConfig
import no.nav.paw.kafkakeygenerator.auth.azureAdM2MTokenClient
import no.nav.paw.kafkakeygenerator.auth.currentNaisEnv
import no.nav.paw.pdl.PdlClient
import no.nav.paw.pdl.graphql.generated.hentforenkletstatus.Person
import no.nav.paw.pdl.hentForenkletStatus

fun interface PdlHentForenkletStatus {
    fun hentForenkletStatus(ident: String, callId: String, navConsumerId: String): Person?

    companion object {
        fun create(): PdlHentForenkletStatus {
            val pdlClient = createPdlClient()
            return PdlHentForenkletStatus { ident, callId, navConsumerId ->
                runBlocking {
                    pdlClient.hentForenkletStatus(ident = ident, callId = callId, navConsumerId = navConsumerId)
                }
            }
        }
    }
}

private fun createPdlClient(): PdlClient {
    val naisEnv = currentNaisEnv
    val azureM2MConfig = loadNaisOrLocalConfiguration<AzureM2MConfig>("azure_m2m.toml")
    val m2mTokenClient = azureAdM2MTokenClient(naisEnv, azureM2MConfig)
    val pdlConfig = loadNaisOrLocalConfiguration<PdlConfig>(PDL_CONFIG_FILE)

    return PdlClient(pdlConfig.url, pdlConfig.tema, createHttpClient()) {
        m2mTokenClient.createMachineToMachineToken(pdlConfig.scope)
    }
}

private fun createHttpClient() = HttpClient {
    install(ContentNegotiation) {
        jackson()
    }
}