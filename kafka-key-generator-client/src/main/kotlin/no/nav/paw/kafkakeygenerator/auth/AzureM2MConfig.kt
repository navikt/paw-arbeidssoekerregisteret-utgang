package no.nav.paw.kafkakeygenerator.auth

data class AzureM2MConfig(
    val tokenEndpointUrl: String,
    val clientId: String
)