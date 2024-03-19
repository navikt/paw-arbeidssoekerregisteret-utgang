package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentPerson
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

fun scheduleAvsluttPerioder(
    ctx: ProcessorContext<Long, Avsluttet>,
    stateStore: KeyValueStore<Long, Periode>,
    interval: Duration = Duration.ofDays(1),
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    pdlHentPerson: PdlHentPerson
) = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) {

    val logger = LoggerFactory.getLogger("scheduleAvsluttPerioder")

    try {
        stateStore.all().forEachRemaining { keyValue ->
            val periode = keyValue.value
            val result =
                pdlHentPerson.hentPerson(
                    periode.identitetsnummer,
                    UUID.randomUUID().toString(),
                    "paw-arbeidssoekerregisteret-utgang-pdl"
                )
            if (result == null) {
                logger.error("Fant ikke person i PDL for periode: $periode")
                return@forEachRemaining
            }
            if (result.folkeregisterpersonstatus.any { it.forenkletStatus !== "bosattEtterFolkeregisterloven" }) {
                val aarsaker =
                    result.folkeregisterpersonstatus.joinToString(separator = ", ") { it.forenkletStatus }

                val (id, newKey) = idAndRecordKeyFunction(periode.identitetsnummer)
                val avsluttetHendelse =
                    Avsluttet(
                        hendelseId = UUID.randomUUID(),
                        id = id,
                        identitetsnummer = periode.identitetsnummer,
                        metadata = Metadata(
                            tidspunkt = Instant.now(),
                            aarsak = "Periode stoppet pga. $aarsaker",
                            kilde = "PDL-utgang",
                            utfoertAv = Bruker(
                                type = BrukerType.SYSTEM,
                                id = ApplicationInfo.id
                            )
                        )
                    )

                val record =
                    Record(newKey, avsluttetHendelse, avsluttetHendelse.metadata.tidspunkt.toEpochMilli())
                ctx.forward(record)
            }
        }
    } catch (e: Exception) {
        logger.error("Feil i skedulert oppgave: $e", e)
        throw e
    }
}