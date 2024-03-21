package no.nav.paw.arbeidssoekerregisteret.app.functions


import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.KafkaIdAndRecordKeyFunction
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
import java.time.Instant

fun KStream<Long, Periode>.lagreEllerSlettPeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction
): KStream<Long, Periode> {
    val processor = {
        PeriodeProsessor(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdFun)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class PeriodeProsessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction
) : Processor<Long, Periode, Long, Periode> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, Periode>? = null
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun init(context: ProcessorContext<Long, Periode>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
        val kvStore = stateStore
        if (kvStore != null && context != null) {
            context.schedule(Duration.ofMinutes(5), PunctuationType.WALL_CLOCK_TIME) {
                val partition = context.taskId().partition()
                val elements = kvStore.all().use { iterator ->
                    iterator.asSequence().count()
                }
                logger.info("Partition $partition has $elements elements, and stream time: ${Instant.ofEpochMilli(context.currentStreamTimeMs())}")
            }
        }
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
}
