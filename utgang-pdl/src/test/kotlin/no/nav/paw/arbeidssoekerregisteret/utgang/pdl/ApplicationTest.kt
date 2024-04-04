package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Opplysning
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata as MetadataIntern
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker as BrukerIntern
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType as BrukerTypeIntern
import java.time.Duration
import java.time.Instant
import java.util.*

class ApplicationTest : FreeSpec({
    "Sender ikke Avsluttet hendelse for person med forenkletStatus 'bosattEtterFolkeregisterloven' i PDL" {
        with(testScope()) {
            periodeTopic.pipeInput(
                Periode(
                    UUID.randomUUID(),
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
            hendelseloggOutputTopic.isEmpty shouldBe true
        }
    }

    "Sender ikke Avsluttet hendelse om hentPersonBolk status er 'bad_request' eller 'not_found' i PDL" {
        with(testScope()) {
            periodeTopic.pipeInput(
                Periode(
                    UUID.randomUUID(),
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
                Periode(
                    UUID.randomUUID(),
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
            hendelseloggOutputTopic.isEmpty shouldBe true
        }
    }

    "Sender ikke Avsluttet hendelse om forenkletStatus 'ikkeBosatt' i PDL og opplysning inneholder både FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT" {
        with(testScope()) {
            hendelseLoggInputTopic.pipeInput(
                Startet(
                    UUID.randomUUID(),
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
                        Opplysning.FORHAANDSGODKJENT_AV_ANSATT,
                        Opplysning.IKKE_BOSATT
                    )
                )
            )
            periodeTopic.pipeInput(
                Periode(
                    UUID.randomUUID(),
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
            hendelseloggOutputTopic.isEmpty shouldBe true
        }
    }

    "Sender Avsluttet hendelse om forenkletStatus 'ikkeBosatt' i PDL og opplysning ikke inneholder både FORHAANDSGODKJENT_AV_ANSATT og IKKE_BOSATT" {
        with(testScope()) {
            hendelseLoggInputTopic.pipeInput(
                Startet(
                    UUID.randomUUID(),
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
                        Opplysning.FORHAANDSGODKJENT_AV_ANSATT
                    )
                )
            )
            periodeTopic.pipeInput(
                Periode(
                    UUID.randomUUID(),
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
        }
    }

    "Sender Avsluttet hendelse med årsak for person uten forenkletStatus 'bosattEtterFolkeregisterloven' i PDL" {
        with(testScope()) {
            periodeTopic.pipeInput(
                Periode(
                    UUID.randomUUID(),
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
        }
    }

})