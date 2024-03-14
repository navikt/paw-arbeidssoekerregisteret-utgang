package no.nav.paw.kafkakeygenerator.auth

data class AuthProvider(
    val name: String,
    val discoveryUrl: String,
    val tokenEndpointUrl: String,
    val clientId: String,
    val claims: List<String>
)