package no.nav.paw.arbeidssoekerregisteret.app.vo


import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory


fun KStream<String, FormidlingsgruppeHendelse>.filterePaaAktivePeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdfun: (String) -> Long
): KStream<String, FormidlingsgruppeHendelse> {
    val processor = {
        FormidlingsgruppeProsessor(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdfun)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class FormidlingsgruppeProsessor(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: (String) -> Long
) : Processor<String, FormidlingsgruppeHendelse, String, FormidlingsgruppeHendelse> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<String, FormidlingsgruppeHendelse>? = null
    private val logger = LoggerFactory.getLogger("applicationTopology")

    override fun init(context: ProcessorContext<String, FormidlingsgruppeHendelse>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
    }


    override fun process(record: Record<String, FormidlingsgruppeHendelse>?) {
        if (record == null) return
        val store = requireNotNull(stateStore) { "State store is not initialized" }
        val storeKey = arbeidssoekerIdFun(record.value().foedselsnummer.foedselsnummer)
        val ctx = requireNotNull(context) { "Context is not initialized" }
        if (store.get(storeKey) != null) {
            ctx.forward(record)
        }
    }
}
