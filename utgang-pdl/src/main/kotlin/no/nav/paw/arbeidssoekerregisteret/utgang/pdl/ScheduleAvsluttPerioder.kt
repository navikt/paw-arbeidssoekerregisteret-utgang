package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentForenkletStatus
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import no.nav.paw.pdl.PdlException
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
    pdlHentForenkletStatus: PdlHentForenkletStatus
) = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) {

    val logger = LoggerFactory.getLogger("scheduleAvsluttPerioder")
    // Totalt antall i stateStore
    // Antall kall
    // Antall feilende kall
    // Antall avsluttet
    try {
        stateStore.all().use { iterator ->
            iterator.asSequence().forEach { keyValue ->
                val periode = keyValue.value
                val result =
                    try {
                        pdlHentForenkletStatus.hentForenkletStatus(
                            periode.identitetsnummer,
                            UUID.randomUUID().toString(),
                            "paw-arbeidssoekerregisteret-utgang-pdl"
                        )
                    } catch (e: PdlException) {
                        logger.error("PDL hentForenkletStatus feiler med: $e", e)
                        return@forEach
                    }

                if (result == null) {
                    logger.error("Fant ikke person i PDL for periode: $periode")
                    return@forEach
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
                                aarsak = aarsaker,
                                kilde = "paw-arbeidssoekerregisteret-utgang-pdl",
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
        }
    } catch (e: Exception) {
        logger.error("Feil i skedulert oppgave: $e", e)
        throw e
    }
}