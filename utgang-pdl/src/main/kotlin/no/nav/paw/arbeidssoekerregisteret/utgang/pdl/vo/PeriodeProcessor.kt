package no.nav.paw.arbeidssoekerregisteret.app.vo

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.ApplicationInfo
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.PdlHentPerson
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

fun KStream<Long, Periode>.lagreEllerSlettPeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    pdlHentPerson: PdlHentPerson
): KStream<Long, Avsluttet> {
    val processor = {
        PeriodeProcessor(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdFun, pdlHentPerson)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class PeriodeProcessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    private val pdlHentPerson: PdlHentPerson
) : Processor<Long, Periode, Long, Avsluttet> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, Avsluttet>? = null
    private val logger = LoggerFactory.getLogger("periodeProsessor")

    override fun init(context: ProcessorContext<Long, Avsluttet>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
        scheduleAvsluttPerioder(
            requireNotNull(context),
            requireNotNull(stateStore),
            Duration.ofDays(1),
            arbeidssoekerIdFun
        )
    }

    override fun process(record: Record<Long, Periode>?) {
        if (record == null) return
        val store = requireNotNull(stateStore) { "State store is not initialized" }
        val storeKey = arbeidssoekerIdFun(record.value().identitetsnummer).id
        if (record.value().avsluttet == null) {
            store.put(storeKey, record.value())
        } else {
            store.delete(storeKey)
        }
    }

    private fun scheduleAvsluttPerioder(
        ctx: ProcessorContext<Long, Avsluttet>,
        stateStore: KeyValueStore<Long, Periode>,
        interval: Duration = Duration.ofDays(1),
        idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction
    ) = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) {
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
}
