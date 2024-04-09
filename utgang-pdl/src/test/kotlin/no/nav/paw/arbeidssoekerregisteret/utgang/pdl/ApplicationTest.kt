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
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.HentPersonBolkResult
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Person
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

    "Sender ikke Avsluttet hendelse for person med forenkletStatus 'ikkeBosatt' i PDL og opplysninger FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT fra hendelser" {
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

    "Sender Avsluttet hendelse for person med forenkletStatus 'ikkeBosatt' i PDL og opplysninger FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT fra hendelser" {
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

    "Sender Avsluttet hendelse for person om forenkletStatus er 'opphoert' i PDL og opplysninger er 'FORHAANDSGODKJENT_AV_ANSATT' og 'DNUMMER'" {
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

    "Fjerner hendelse fra hendelse state store hvis ingen tilhørende periode har kommet innen 30 dager" {
        with(testScope(generatePdlMockResponse("12345678906", listOf("opphoert"), "ok"))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1234L,
                    "12345678906",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678906"
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
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(31))
            hendelseKeyValueStore.get(periodeId) shouldBe null
        }
    }

    "Fjerner ikke hendelse fra hendelse state store hvis ingen tilhørende periode har kommet innen 29 dager" {
        with(testScope(generatePdlMockResponse("12345678906", listOf("opphoert"), "ok"))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1234L,
                    "12345678906",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678906"
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
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(29))
            hendelseKeyValueStore.all().asSequence().count() shouldBe 1
        }
    }

    "Fjerner ikke hendelse fra hendelse state store hvis tilhørende periode har kommet innen 30 dager" {
        with(testScope(generatePdlMockResponse("12345678906", listOf("bosattEtterFolkeregisterloven"), "ok"))) {
            verifyEmptyTopic(hendelseloggOutputTopic)
            hendelseloggInputTopic.pipeInput(
                1234L,
                Startet(
                    periodeId,
                    1234L,
                    "12345678906",
                    MetadataIntern(
                        Instant.now(),
                        BrukerIntern(
                            BrukerTypeIntern.SLUTTBRUKER,
                            "12345678906"
                        ),
                        "",
                        ""
                    ),
                    emptySet()
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(10))
            periodeTopic.pipeInput(
                1234L,
                Periode(
                    periodeId,
                    "12345678906",
                    Metadata(
                        Instant.now(),
                        Bruker(
                            BrukerType.SLUTTBRUKER,
                            "12345678906"
                        ),
                        "",
                        ""
                    ),
                    null
                )
            )
            topologyTestDriver.advanceWallClockTime(Duration.ofDays(21))
            hendelseKeyValueStore.all().asSequence().count() shouldBe 1
        }
    }

    "avsluttPeriodeGrunnlag skal returnere en liste med opplysning DOED om forenkletStatus er 'doedIFolkeregisteret'" {
        val folkeregisterpersonstatus =
            listOf(
                Folkeregisterpersonstatus("doedIFolkeregisteret"),
                Folkeregisterpersonstatus("dNummer")
            )

        val opplysningerFraHendelseState = emptySet<Opplysning>()
        avsluttPeriodeGrunnlag(folkeregisterpersonstatus, opplysningerFraHendelseState) shouldBe listOf(Opplysning.DOED)
    }

    "avsluttPeriodeGrunnlag skal returnere en tom liste med grunnlag for avsluttet periode om forenkletStatus er 'ikkeBosatt' og opplysninger FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT fra hendelser" {
        val folkeregisterpersonstatus =
            listOf(
                Folkeregisterpersonstatus("ikkeBosatt"),
            )

        val opplysningerFraHendelseState = setOf(
            Opplysning.FORHAANDSGODKJENT_AV_ANSATT,
            Opplysning.IKKE_BOSATT
        )
        avsluttPeriodeGrunnlag(folkeregisterpersonstatus, opplysningerFraHendelseState) shouldBe emptySet()
    }

    "avsluttPeriodeGrunnlag skal retunere en liste med opplysning opphoert om forenkletStatus er 'opphoert' og opplysninger FORHAANDSGODKJENT_AV_ANSATT og DNUMMER fra hendelser" {
        val folkeregisterpersonstatus =
            listOf(
                Folkeregisterpersonstatus("opphoert"),
            )

        val opplysningerFraHendelseState = setOf(
            Opplysning.DNUMMER,
            Opplysning.FORHAANDSGODKJENT_AV_ANSATT
        )
        avsluttPeriodeGrunnlag(folkeregisterpersonstatus, opplysningerFraHendelseState) shouldBe listOf(Opplysning.OPPHOERT_IDENTITET)
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

fun generatePdlMockResponse(ident: String, forenkletStatus: List<String>, status: String = "ok") = listOf(
    HentPersonBolkResult(
        ident,
        person = Person(
            forenkletStatus.map {
                Folkeregisterpersonstatus(
                    it,
                )
            },
        ),
        code = status,
    )
)