package no.nav.paw.arbeidssoekerregisteret.app.vo


import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.ZoneOffset


fun KStream<Long, FormidlingsgruppeHendelse>.filterePaaAktivePeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdfun: KafkaIdAndRecordKeyFunction
): KStream<Long, FormidlingsgruppeHendelse> {
    val processor = {
        FormidlingsgruppeProsessor(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdfun)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class FormidlingsgruppeProsessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction
) : Processor<Long, FormidlingsgruppeHendelse, Long, FormidlingsgruppeHendelse> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, FormidlingsgruppeHendelse>? = null
    private val logger = LoggerFactory.getLogger("applicationTopology")

    override fun init(context: ProcessorContext<Long, FormidlingsgruppeHendelse>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
    }


    override fun process(record: Record<Long, FormidlingsgruppeHendelse>?) {
        if (record == null) return
        val store = requireNotNull(stateStore) { "State store is not initialized" }
        val ctx = requireNotNull(context) { "Context is not initialized" }
        val storeKey =
            record.value().idFraKafkaKeyGenerator ?: arbeidssoekerIdFun(
                record.value().foedselsnummer.foedselsnummer
            ).id
        val periode = store.get(storeKey) ?: return
        val startTime = periode.startet.tidspunkt
        val requestedStopTime = record.value().formidlingsgruppeEndret.toInstant(ZoneOffset.of(ZoneOffset.systemDefault().id))

    }
}
