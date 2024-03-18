package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.config

const val APPLICATION_CONFIG_FILE = "application_config.toml"
data class ApplicationConfiguration(
    val periodeTopic: String,
    val hendelseloggTopic: String,
    val applicationIdSuffix: String,
    val aktivePerioderStateStoreName: String
)