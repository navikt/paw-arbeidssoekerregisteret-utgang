package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import java.time.Duration
import java.time.Instant
import java.util.*

class ApplicationTest : FreeSpec({
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
            hendelseloggTopic.isEmpty shouldBe false
        }
    }

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
            hendelseloggTopic.isEmpty shouldBe true
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
            hendelseloggTopic.isEmpty shouldBe true
        }
    }
})