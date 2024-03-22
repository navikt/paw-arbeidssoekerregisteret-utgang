package no.nav.paw.arbeidssoekerregisteret.app

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.paw.arbeidssoekerregisteret.app.vo.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import java.time.*
import java.time.Duration.between
import java.time.Duration.ofSeconds
import java.time.format.DateTimeFormatter
import java.util.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata as ApiMetadata

class ApplicationTest : FreeSpec({
    with(testScope()) {
        val localNow = LocalDateTime.of(2024, Month.MARCH, 3, 15, 42)
        val instantNow = localNow.atZone(ZoneId.of("Europe/Oslo")).toInstant()
        "Verifiser applikasjonsflyt" - {
            "Verifiser serializering/deserializering" {
                val hendelse = formilingsgruppeHendelse(
                    foedselsnummer = "12345678901",
                    formidlingsgruppe = iarbs,
                    formidlingsgruppeEndret = LocalDateTime.now()
                )
                val data = ArenaFormidlingsgruppeSerde().serializer().serialize("topic", hendelse)
                println("deserialized: ${String(data)}")
                val deserialized = ArenaFormidlingsgruppeSerde().deserializer().deserialize("topic", data)
                deserialized shouldBe hendelse
            }
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
                kevValueStore.get(1L) shouldBe periodeStart
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
                    "Some random key",
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
                kv.value.id shouldBe kafkaKeysClient(periodeStart.identitetsnummer)?.id
                between(kv.value.metadata.tidspunkt, Instant.now()).abs() shouldBeLessThan ofSeconds(60)
            }
            "Når perioden stoppes slettes den fra state store" {
                periodeTopic.pipeInput(
                    1L,
                    Periode(
                        periodeStart.id,
                        periodeStart.identitetsnummer,
                        periodeStart.startet,
                        ApiMetadata(
                            periodeStart.startet.tidspunkt + 1.dager,
                            bruker,
                            "junit",
                            "iserv"
                        )
                    )
                )
                hendelseloggTopic.isEmpty shouldBe true
                kevValueStore.get(1L) shouldBe null
            }
        }
    }
})

fun formilingsgruppeHendelse(
    foedselsnummer: String,
    formidlingsgruppe: Formidlingsgruppe,
    formidlingsgruppeEndret: LocalDateTime = LocalDateTime.now()
): ArenaFormidlingsruppe =
    ArenaFormidlingsruppe(
        op_type = "insert",
        after = ArenaData(
            personId = "vi leser ikke denne",
            personIdStatus = "vi leser ikke denne heller",
            fodselsnr = foedselsnummer,
            formidlingsgruppekode = formidlingsgruppe.kode,
            modDato = formidlingsgruppeEndret.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ),
        before = null
    )

val iarbs = Formidlingsgruppe("IARBS")
val iserv = Formidlingsgruppe("ISERV")
val arbs = Formidlingsgruppe("ARBS")

val Int.dager: Duration get() = Duration.ofDays(this.toLong())

val bruker = Bruker(
    BrukerType.SYSTEM,
    "junit"
)