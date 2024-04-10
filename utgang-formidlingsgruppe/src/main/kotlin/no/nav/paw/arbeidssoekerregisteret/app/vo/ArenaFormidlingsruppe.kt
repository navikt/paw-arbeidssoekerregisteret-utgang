package no.nav.paw.arbeidssoekerregisteret.app.vo

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ArenaFormidlingsruppe(
    val op_type: String,
    val after: ArenaData?,
    val before: ArenaData?
)

@JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy::class)
data class ArenaData(
    val personId: String,
    val personIdStatus: String,
    val fodselsnr: String?,
    val formidlingsgruppekode: String,
    val modDato: String
)

private val datoFormatLogger = LoggerFactory.getLogger("Datoformat")
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun ArenaFormidlingsruppe.validValuesOrNull(): FormidlingsgruppeData? {
    val foedselsnummer = after?.fodselsnr?.let(::Foedselsnummer) ?: return null
    val formidlingsgruppe = Formidlingsgruppe(after.formidlingsgruppekode)
    val formidlingsgruppeEndret = LocalDateTime.parse(after.modDato, formatter)
        .atZone(ZoneId.of("Europe/Oslo")).toInstant()

    datoFormatLogger.info("endret dato: ${after.modDato}, parsed=$formidlingsgruppeEndret")
    return FormidlingsgruppeData(
        opType = OpType.ofShortValue(op_type),
        foedselsnummer = foedselsnummer,
        formidlingsgruppe = formidlingsgruppe,
        formidlingsgruppeEndret = formidlingsgruppeEndret
    )
}

data class FormidlingsgruppeData(
    val opType: OpType,
    val foedselsnummer: Foedselsnummer,
    val formidlingsgruppe: Formidlingsgruppe,
    val formidlingsgruppeEndret: Instant
)

enum class OpType {
    INSERT,
    UPDATE,
    DELETE;
    companion object {
        fun ofShortValue(value: String) = when (value.uppercase()) {
            "I" -> INSERT
            "U" -> UPDATE
            "D" -> DELETE
            else -> throw IllegalArgumentException("Ukjent op_type-verdi på Kafka: $value")
        }
    }
}
