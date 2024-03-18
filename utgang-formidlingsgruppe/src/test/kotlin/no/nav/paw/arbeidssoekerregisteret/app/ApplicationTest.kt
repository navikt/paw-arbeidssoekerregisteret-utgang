package no.nav.paw.arbeidssoekerregisteret.app

import io.kotest.core.spec.style.FreeSpec
import no.nav.paw.arbeidssoekerregisteret.app.vo.Foedselsnummer
import no.nav.paw.arbeidssoekerregisteret.app.vo.Formidlingsgruppe
import no.nav.paw.arbeidssoekerregisteret.app.vo.FormidlingsgruppeHendelse
import no.nav.paw.arbeidssoekerregisteret.app.vo.Operation
import java.time.LocalDateTime

class ApplicationTest : FreeSpec({
    with(testScope()) {
        "Verifiser applikasjonsflyt" - {
            "Når vi mottar IARBS uten noen periode skjer det ingenting" {

            }
        }
    }
})

fun formilingsgruppeHendelse(
    foedselsnummer: String,
    formidlingsgruppe: String,
    formidlingsgruppeEndret: LocalDateTime = LocalDateTime.now(),
    forrigeFormidlingsgruppe: Formidlingsgruppe? = null,
    operation: Operation = Operation.INSERT,
    personStatus: String = "AKTIV"
): FormidlingsgruppeHendelse =
    FormidlingsgruppeHendelse(
        foedselsnummer = Foedselsnummer(foedselsnummer),
        formidlingsgruppe = Formidlingsgruppe(formidlingsgruppe),
        idFraKafkaKeyGenerator = null,
        forrigeFormidlingsgruppeEndret = null,
        formidlingsgruppeEndret = formidlingsgruppeEndret,
        forrigeFormidlingsgruppe = forrigeFormidlingsgruppe,
        personId = foedselsnummer,
        operation = operation,
        personIdStatus = personStatus
    )