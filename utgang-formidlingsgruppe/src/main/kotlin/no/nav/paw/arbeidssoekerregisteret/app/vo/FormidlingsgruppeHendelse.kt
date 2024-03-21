package no.nav.paw.arbeidssoekerregisteret.app.vo

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class FormidlingsgruppeHendelse (
    val foedselsnummer: Foedselsnummer?,
    val personId: String?,
    val personIdStatus: String?,
    val operation: Operation?,
    val formidlingsgruppe: Formidlingsgruppe?,
    val formidlingsgruppeEndret: LocalDateTime?,
    val forrigeFormidlingsgruppe: Formidlingsgruppe?,
    val forrigeFormidlingsgruppeEndret: LocalDateTime?
)

fun FormidlingsgruppeHendelse.validValuesOrNull(): Triple<Foedselsnummer, Formidlingsgruppe, Instant>? {
    val foedselsnummer = foedselsnummer ?: return null
    val formidlingsgruppe = formidlingsgruppe ?: return null
    val formidlingsgruppeEndret = formidlingsgruppeEndret?.atZone(ZoneId.of("Europe/Oslo"))?.toInstant() ?: return null
    return Triple(foedselsnummer, formidlingsgruppe, formidlingsgruppeEndret)
}

data class Foedselsnummer(val foedselsnummer: String)

enum class Operation {
    INSERT, UPDATE, DELETE
}

data class Formidlingsgruppe(val kode: String)