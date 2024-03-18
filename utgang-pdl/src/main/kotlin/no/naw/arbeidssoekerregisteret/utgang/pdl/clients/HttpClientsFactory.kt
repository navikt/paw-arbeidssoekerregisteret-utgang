package no.naw.arbeidssoekerregisteret.utgang.pdl.clients

import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.kafkakeygenerator.auth.AuthProvider
import no.nav.paw.kafkakeygenerator.auth.azureAdM2MTokenClient
import no.nav.paw.kafkakeygenerator.auth.currentNaisEnv
import no.nav.paw.kafkakeygenerator.client.KafkaKeyConfig
import no.nav.paw.kafkakeygenerator.client.KafkaKeysClient
import no.nav.paw.kafkakeygenerator.client.kafkaKeysKlient

fun createKafkaKeyGeneratorClient(): KafkaKeysClient {
    val naisEnv = currentNaisEnv
    val authProvider = loadNaisOrLocalConfiguration<AuthProvider>("tokenx.toml")
    val kafkaKeyConfig = loadNaisOrLocalConfiguration<KafkaKeyConfig>("kafka_key_generator_client_config.toml")
    val m2mTokenClient = azureAdM2MTokenClient(naisEnv, authProvider)
    return kafkaKeysKlient(kafkaKeyConfig) {
        m2mTokenClient.createMachineToMachineToken(kafkaKeyConfig.scope)
    }
}
