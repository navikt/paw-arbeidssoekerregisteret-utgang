package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentForenkletStatus
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import java.time.Duration

fun KStream<Long, Periode>.lagreEllerSlettPeriode(
    periodeStateStoreName: String,
    hendelseStateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    pdlHentForenkletStatus: PdlHentForenkletStatus
): KStream<Long, Avsluttet> {
    val processor = {
        PeriodeProcessor(
            periodeStateStoreName,
            hendelseStateStoreName,
            prometheusMeterRegistry,
            arbeidssoekerIdFun,
            pdlHentForenkletStatus
        )
    }
    return process(processor, Named.`as`("periodeProsessor"), periodeStateStoreName, hendelseStateStoreName)
}

class PeriodeProcessor(
    private val periodeStateStoreName: String,
    private val hendelseStateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
    private val pdlHentForenkletStatus: PdlHentForenkletStatus
) : Processor<Long, Periode, Long, Avsluttet> {
    private var periodeStateStore: KeyValueStore<Long, Periode>? = null
    private var hendelseStateStore: KeyValueStore<Long, Startet>? = null
    private var context: ProcessorContext<Long, Avsluttet>? = null

    override fun init(context: ProcessorContext<Long, Avsluttet>?) {
        super.init(context)
        this.context = context
        periodeStateStore = context?.getStateStore(periodeStateStoreName)
        hendelseStateStore = context?.getStateStore(hendelseStateStoreName)
        scheduleAvsluttPerioder(
            requireNotNull(context),
            requireNotNull(periodeStateStore),
            requireNotNull(hendelseStateStore),
            Duration.ofDays(1),
            arbeidssoekerIdFun,
            pdlHentForenkletStatus,
            prometheusMeterRegistry
        )
    }

    override fun process(record: Record<Long, Periode>?) {
        if (record == null) return
        val store = requireNotNull(periodeStateStore) { "State store is not initialized" }
        val storeKey = arbeidssoekerIdFun(record.value().identitetsnummer).id
        if (record.value().avsluttet == null) {
            store.put(storeKey, record.value())
        } else {
            store.delete(storeKey)
        }
    }
}
