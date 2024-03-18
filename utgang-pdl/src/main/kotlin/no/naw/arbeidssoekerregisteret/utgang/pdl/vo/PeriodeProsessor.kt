package no.nav.paw.arbeidssoekerregisteret.app.vo

// noe skrive feil i pathen her, det står no.naw.arbeids... CWM støtter ikke endring av mapper :(
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.pdl.PdlClient
import no.nav.paw.pdl.hentPerson
import no.naw.arbeidssoekerregisteret.utgang.pdl.clients.KafkaIdAndRecordKeyFunction
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

fun KStream<Long, Periode>.lagreEllerSlettPeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    pdlClient: PdlClient
): KStream<Long, Periode> {
    val processor = {
        PeriodeProsessor(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdFun, pdlClient)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class PeriodeProsessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    private val pdlClient: PdlClient
) : Processor<Long, Periode, Long, Periode> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, Periode>? = null
    private val logger = LoggerFactory.getLogger("periodeProsessor")

    override fun init(context: ProcessorContext<Long, Periode>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
        scheduleAvsluttPerioder(
            requireNotNull(context),
            requireNotNull(stateStore)
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
        ctx: ProcessorContext<Long, Periode>,
        stateStore: KeyValueStore<Long, Periode>,
        interval: Duration = Duration.ofDays(1)
    ) = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) {
        try {
            stateStore.all().forEachRemaining { keyValue ->
                val periode = keyValue.value
                val result = runBlocking {
                    pdlClient.hentPerson(
                        ident = periode.identitetsnummer,
                        callId = UUID.randomUUID().toString(),
                        navConsumerId = "paw-arbeidssoekerregisteret-utgang-pdl"
                    )
                }
                if (result == null) {
                    logger.error("Fant ikke person i PDL for periode: $periode")
                    return@forEachRemaining
                }
                if (result.folkeregisterpersonstatus.any { it.forenkletStatus !== "bosattEtterFolkeregisterloven" }) {
                    val record = Record(keyValue.key, keyValue.value, keyValue.value.avsluttet.tidspunkt.toEpochMilli())
                    ctx.forward(record)
                }
            }
        } catch (e: Exception) {
            logger.error("Feil i skedulert oppgave: $e", e)
            throw e
        }
    }
}
