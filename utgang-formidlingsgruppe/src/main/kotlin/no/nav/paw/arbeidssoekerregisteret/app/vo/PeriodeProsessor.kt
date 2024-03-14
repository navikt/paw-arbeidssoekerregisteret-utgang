package no.nav.paw.arbeidssoekerregisteret.app.vo


import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

fun KStream<Long, Periode>.lagreEllerSlettPeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry
): KStream<Long, Periode> {
    val processor = {
        PeriodeProsessor(stateStoreName, prometheusMeterRegistry)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class PeriodeProsessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry
) : Processor<Long, Periode, Long, Periode> {
    private var stateStore: KeyValueStore<UUID, Periode>? = null
    private var context: ProcessorContext<Long, Periode>? = null
    private val logger = LoggerFactory.getLogger("applicationTopology")

    override fun init(context: ProcessorContext<Long, Periode>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
        scheduleSjekkPDLstatus(
            requireNotNull(context) { "Context is not initialized" },
            requireNotNull(stateStore) { "State store is not initialized" }
        )
    }

    private fun scheduleSjekkPDLstatus(
        ctx: ProcessorContext<Long, Periode>,
        stateStore: KeyValueStore<UUID, Periode>,
        interval: Duration = Duration.ofMinutes(10)
    ) = ctx.schedule(interval, PunctuationType.STREAM_TIME) { time ->

    }

    override fun process(record: Record<Long, Periode>?) {
        if (record == null) return
        val store = requireNotNull(stateStore) { "State store is not initialized" }
        val storeKey = record.value().id
        if (record.value().avsluttet == null) {
            store.put(storeKey, record.value())
        } else {
            store.delete(storeKey)
        }
    }
}
