package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

const val APPLICATION_CONFIG_FILE = "application_config.toml"
data class ApplicationConfiguration(
    val applicationIdSuffix: String,
    val periodeTopic: String,
    val hendelseloggTopic: String,
    val hendelseStateStoreName: String,
)