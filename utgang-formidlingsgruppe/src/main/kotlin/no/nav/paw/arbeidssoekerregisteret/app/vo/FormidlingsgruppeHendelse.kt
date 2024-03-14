package no.nav.paw.arbeidssoekerregisteret.app.vo

import java.time.LocalDateTime

data class FormidlingsgruppeHendelse (
    val foedselsnummer: Foedselsnummer,
    val personId: String,
    val personIdStatus: String,
    val operation: Operation,
    val formidlingsgruppe: Formidlingsgruppe,
    val formidlingsgruppeEndret: LocalDateTime,
    val forrigeFormidlingsgruppe: Formidlingsgruppe?,
    val forrigeFormidlingsgruppeEndret: LocalDateTime?
)


data class Foedselsnummer(val foedselsnummer: String)

enum class Operation {
    INSERT, UPDATE, DELETE
}

data class Formidlingsgruppe(val kode: String)