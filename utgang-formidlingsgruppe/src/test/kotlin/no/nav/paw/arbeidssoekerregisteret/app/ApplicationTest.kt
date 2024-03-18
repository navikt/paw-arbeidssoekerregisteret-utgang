package no.nav.paw.arbeidssoekerregisteret.app

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.paw.arbeidssoekerregisteret.app.vo.Foedselsnummer
import no.nav.paw.arbeidssoekerregisteret.app.vo.Formidlingsgruppe
import no.nav.paw.arbeidssoekerregisteret.app.vo.FormidlingsgruppeHendelse
import no.nav.paw.arbeidssoekerregisteret.app.vo.Operation
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import java.time.*
import java.time.Duration.between
import java.time.Duration.ofSeconds
import java.util.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata as ApiMetadata

class ApplicationTest : FreeSpec({
    with(testScope()) {
        val localNow = LocalDateTime.of(2024, Month.MARCH, 3, 15, 42)
        val instantNow = localNow.atZone(ZoneId.of("Europe/Oslo")).toInstant()
        "Verifiser applikasjonsflyt" - {
            "Når vi mottar IARBS uten noen periode skjer det ingenting" {
                formidlingsgruppeTopic.pipeInput(
                    formilingsgruppeHendelse(
                        foedselsnummer = "12345678901",
                        formidlingsgruppe = iarbs,
                        formidlingsgruppeEndret = localNow
                    )
                )
                hendelseloggTopic.isEmpty shouldBe true
            }
            val periodeStart = Periode(
                UUID.randomUUID(),
                "12345678901",
                ApiMetadata(
                    instantNow.minus(10.dager),
                    bruker,
                    "junit",
                    "testing"
                ),
                null
            )
            "Når vi mottar periode start skjer det ingenting" {
                periodeTopic.pipeInput(1L, periodeStart)
                hendelseloggTopic.isEmpty shouldBe true
            }
            "Når vi mottar ISERV datert før periode start skjer det ingenting" {
                formidlingsgruppeTopic.pipeInput(
                    formilingsgruppeHendelse(
                        foedselsnummer = periodeStart.identitetsnummer,
                        formidlingsgruppe = iserv,
                        formidlingsgruppeEndret = localNow.minus(11.dager)
                    )
                )
                hendelseloggTopic.isEmpty shouldBe true
            }
            "Når vi mottar IARBS datert etter periode start skjer det ingenting" {
                formidlingsgruppeTopic.pipeInput(
                    formilingsgruppeHendelse(
                        foedselsnummer = periodeStart.identitetsnummer,
                        formidlingsgruppe = iarbs,
                        formidlingsgruppeEndret = localNow.plus(1.dager)
                    )
                )
                hendelseloggTopic.isEmpty shouldBe true
            }
            "Når vi mottar ARBS datert etter periode start skjer det ingenting" {
                formidlingsgruppeTopic.pipeInput(
                    formilingsgruppeHendelse(
                        foedselsnummer = periodeStart.identitetsnummer,
                        formidlingsgruppe = arbs,
                        formidlingsgruppeEndret = localNow.plus(1.dager)
                    )
                )
                hendelseloggTopic.isEmpty shouldBe true
            }
            "Når vi mottar ISERV datert etter periode start genereres en 'stoppet' melding" {
                formidlingsgruppeTopic.pipeInput(
                    formilingsgruppeHendelse(
                        foedselsnummer = periodeStart.identitetsnummer,
                        formidlingsgruppe = iserv,
                        formidlingsgruppeEndret = localNow.plus(1.dager)
                    )
                )
                hendelseloggTopic.isEmpty shouldBe false
                val kv = hendelseloggTopic.readKeyValue()
                kv.key shouldBe 1L
                kv.value.shouldBeInstanceOf<Avsluttet>()
                kv.value.id shouldBe kafkaKeysClient(periodeStart.identitetsnummer).id
                between(kv.value.metadata.tidspunkt, Instant.now()).abs() shouldBeLessThan ofSeconds(60)
            }
        }
    }
})

fun formilingsgruppeHendelse(
    foedselsnummer: String,
    formidlingsgruppe: Formidlingsgruppe,
    formidlingsgruppeEndret: LocalDateTime = LocalDateTime.now(),
    forrigeFormidlingsgruppe: Formidlingsgruppe? = null,
    operation: Operation = Operation.INSERT,
    personStatus: String = "AKTIV"
): FormidlingsgruppeHendelse =
    FormidlingsgruppeHendelse(
        foedselsnummer = Foedselsnummer(foedselsnummer),
        formidlingsgruppe = formidlingsgruppe,
        idFraKafkaKeyGenerator = null,
        forrigeFormidlingsgruppeEndret = null,
        formidlingsgruppeEndret = formidlingsgruppeEndret,
        forrigeFormidlingsgruppe = forrigeFormidlingsgruppe,
        personId = foedselsnummer,
        operation = operation,
        personIdStatus = personStatus
    )

val iarbs = Formidlingsgruppe("IARBS")
val iserv = Formidlingsgruppe("ISERV")
val arbs = Formidlingsgruppe("ARBS")

val Int.dager: Duration get() = Duration.ofDays(this.toLong())

val bruker = Bruker(
    BrukerType.SYSTEM,
    "junit"
)