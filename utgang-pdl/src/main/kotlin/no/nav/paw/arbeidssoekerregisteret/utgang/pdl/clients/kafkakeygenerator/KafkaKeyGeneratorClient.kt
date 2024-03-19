package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator

import kotlinx.coroutines.runBlocking
import no.nav.paw.config.hoplite.loadNaisOrLocalConfiguration
import no.nav.paw.kafkakeygenerator.auth.AzureM2MConfig
import no.nav.paw.kafkakeygenerator.auth.azureAdM2MTokenClient
import no.nav.paw.kafkakeygenerator.auth.currentNaisEnv
import no.nav.paw.kafkakeygenerator.client.KafkaKeyConfig
import no.nav.paw.kafkakeygenerator.client.KafkaKeysClient
import no.nav.paw.kafkakeygenerator.client.kafkaKeysKlient

fun createIdAndRecordKeyFunction(): KafkaIdAndRecordKeyFunction =
    with(createKafkaKeyGeneratorClient()) {
        KafkaIdAndRecordKeyFunction { identitetsnummer ->
            runBlocking {
                getIdAndKey(identitetsnummer)
                    .let {
                        IdAndRecordKey(
                            id = it.id,
                            recordKey = it.key
                        )
                    }
            }
        }
    }

fun interface KafkaIdAndRecordKeyFunction {
    operator fun invoke(identitetsnummer: String): IdAndRecordKey
}

data class IdAndRecordKey(
    val id: Long,
    val recordKey: Long
)

private fun createKafkaKeyGeneratorClient(): KafkaKeysClient {
    val naisEnv = currentNaisEnv
    val azureM2MConfig = loadNaisOrLocalConfiguration<AzureM2MConfig>("azure_m2m.toml")
    val kafkaKeyConfig = loadNaisOrLocalConfiguration<KafkaKeyConfig>("kafka_key_generator_client_config.toml")
    val m2mTokenClient = azureAdM2MTokenClient(naisEnv, azureM2MConfig)
    return kafkaKeysKlient(kafkaKeyConfig) {
        m2mTokenClient.createMachineToMachineToken(kafkaKeyConfig.scope)
    }
}