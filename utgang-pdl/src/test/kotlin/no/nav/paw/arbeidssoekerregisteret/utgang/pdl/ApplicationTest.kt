package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.avsluttPeriodeGrunnlag
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Opplysning
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Folkeregisterpersonstatus
import org.apache.kafka.streams.TestOutputTopic
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata as MetadataIntern
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker as BrukerIntern
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType as BrukerTypeIntern
import java.time.Duration
import java.time.Instant
import java.util.*

class ApplicationTest : FreeSpec({
    val periodeId = UUID.randomUUID()
    "Sender Avsluttet hendelse for person med forenkletStatus 'doedIFolkeregisteret' i PDL og ingen opplysninger fra hendelser" {
        with(testScope(generatePdlMockResponse("12345678901", listOf("doedIFolkeregisteret")))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(1234L,
                Startet(
                    periodeId,
                    1234L,
                    "12345678901",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678901"
                        ),
                        "",
                        ""
                    ),
                    emptySet()
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678901",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678901"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            hendelseloggOutputTopic.isEmpty shouldBe false
            hendelseloggOutputTopic.readValue()
        }
    }

    "Sender Avsluttet hendelse for person med forenkletStatus 'bosattEtterFolkeregisterloven' og 'forsvunnet' i PDL og ingen opplysninger fra hendelser" {
        with(testScope(generatePdlMockResponse("12345678902", listOf("bosattEtterFolkeregisterloven", "forsvunnet")))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1234,
                    "12345678902",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678902"
                        ),
                        "",
                        ""
                    ),
                    emptySet()
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678902",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678902"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            hendelseloggOutputTopic.isEmpty shouldBe false
            hendelseloggOutputTopic.readValue()
        }
    }

    "Sender ikke Avsluttet hendelse om hentPersonBolk status er 'bad_request' eller 'not_found' i PDL" {
        with(testScope(generatePdlMockResponse("12345678903", listOf("doedIFolkeregisteret", "dNummer"), "not_found"))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678903",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678903"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678903",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678903"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            verifyEmptyTopic(hendelseloggOutputTopic)
        }
    }

    "Sender ikke Avsluttet hendelse for person med forenkletStatus 'ikkeBosatt' i PDL og opplysning inneholder både FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT" {
        with(testScope(generatePdlMockResponse("12345678904", listOf("ikkeBosatt", "dNummer")))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1L,
                    "12345678904",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.VEILEDER,
                            "1234"
                        ),
                        "",
                        ""
                    ),
                    setOf(
                        Opplysning.DNUMMER,
                        Opplysning.FORHAANDSGODKJENT_AV_ANSATT,
                        Opplysning.IKKE_BOSATT
                    )
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678904",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678904"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            verifyEmptyTopic(hendelseloggOutputTopic)
        }
    }

    "Sender Avsluttet hendelse om forenkletStatus 'ikkeBosatt' i PDL og opplysning ikke inneholder både FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT" {
        with(testScope(generatePdlMockResponse("12345678904", listOf("ikkeBosatt", "dNummer")))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    12344568904,
                    "12345678904",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678904"
                        ),
                        "",
                        ""
                    ),
                    setOf(
                        Opplysning.FORHAANDSGODKJENT_AV_ANSATT,
                        Opplysning.ER_UNDER_18_AAR
                    )
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678904",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678904"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            hendelseloggOutputTopic.isEmpty shouldBe false
            hendelseloggOutputTopic.readValue()
        }
    }

    "Sender Avsluttet hendelse for person med forenkletStatus 'opphoert' i PDL og 'FORHAANDSGODKJENT_AV_ANSATT' og 'DNUMMER' i opplysninger" {
        with(testScope(generatePdlMockResponse("12345678905", listOf("opphoert"), "ok"))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1234L,
                    "12345678905",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678905"
                        ),
                        "",
                        ""
                    ),
                    setOf(
                        Opplysning.DNUMMER,
                        Opplysning.FORHAANDSGODKJENT_AV_ANSATT
                    )
                )
            )
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678905",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678905"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(2))
            hendelseloggOutputTopic.isEmpty shouldBe false
            hendelseloggOutputTopic.readValue()
        }
    }

    "avsluttPeriodeGrunnlag skal returnere en liste med grunnlag for avsluttet periode" {
        val folkeregisterpersonstatus =
            listOf(
                Folkeregisterpersonstatus("doedIFolkeregisteret"),
                Folkeregisterpersonstatus("dNummer")
            )

        val opplysningerFraHendelseState = emptySet<Opplysning>()
        avsluttPeriodeGrunnlag(folkeregisterpersonstatus, opplysningerFraHendelseState) shouldBe listOf(Opplysning.DOED)
    }
})

fun verifyEmptyTopic(hendelseloggOutputTopic: TestOutputTopic<out Any, out Any>) {
    if (hendelseloggOutputTopic.isEmpty) return
    val records = hendelseloggOutputTopic.readKeyValuesToList()
        .map { it.key to it.value }
    fail(
        "Forventet at topic hendelseloggOutputTopic skulle være tom, følgende records ble funnet:\n ${
            records.toList().map { "$it\n" }
        }"
    )
}