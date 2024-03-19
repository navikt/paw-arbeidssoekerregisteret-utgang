package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients

import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.config.PDL_CONFIG_FILE
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.config.PdlConfig
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.kafkakeygenerator.auth.AzureM2MConfig
import no.nav.paw.kafkakeygenerator.auth.azureAdM2MTokenClient
import no.nav.paw.kafkakeygenerator.auth.currentNaisEnv
import no.nav.paw.pdl.PdlClient
import no.nav.paw.pdl.graphql.generated.hentperson.Person
import no.nav.paw.pdl.hentPerson

fun interface PdlHentPerson {
    fun hentPerson(ident: String, callId: String, navConsumerId: String): Person?

    companion object {
        fun create(): PdlHentPerson {
            val pdlClient = createPdlClient()
            return PdlHentPerson { ident, callId, navConsumerId ->
                runBlocking {
                    pdlClient.hentPerson(ident = ident, callId = callId, navConsumerId = navConsumerId)
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