package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Opplysning
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore

fun KStream<Long, Startet>.lagreForhaandsGodkjenteHendelser(
    hendelseStateStoreName: String,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
): KStream<Long, Startet> {
    val processor = {
        HendelseLoggStartetProcessor(hendelseStateStoreName, arbeidssoekerIdFun)
    }
    return process(processor, Named.`as`("hendelseLoggStartetProsessor"), hendelseStateStoreName)
}

fun KStream<Long, Avsluttet>.slettAvsluttetHendelser(
    hendelseStateStoreName: String,
    arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
): KStream<Long, Avsluttet> {
    val processor = {
        HendelseLoggAvsluttetProcessor(hendelseStateStoreName, arbeidssoekerIdFun)
    }
    return process(processor, Named.`as`("hendelseLoggAvsluttetProsessor"), hendelseStateStoreName)
}

class HendelseLoggStartetProcessor(
    private val hendelseStateStoreName: String,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
) : Processor<Long, Startet, Long, Startet> {
    private var hendelseStateStore: KeyValueStore<Long, Startet>? = null
    private var context: ProcessorContext<Long, Startet>? = null

    override fun init(context: ProcessorContext<Long, Startet>?) {
        super.init(context)
        this.context = context
        hendelseStateStore = context?.getStateStore(hendelseStateStoreName)
    }

    override fun process(record: Record<Long, Startet>?) {
        if (record == null) return
        val store = requireNotNull(hendelseStateStore) { "State store is not initialized" }
        val storeKey = arbeidssoekerIdFun(record.value().identitetsnummer).id
        val startetHendelse = record.value()
        if (Opplysning.FORHAANDSGODKJENT_AV_ANSATT in startetHendelse.opplysninger) {
            store.put(storeKey, startetHendelse)
        } else {
            store.delete(storeKey)
        }
    }
}

class HendelseLoggAvsluttetProcessor(
    private val hendelseStateStoreName: String,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction,
) : Processor<Long, Avsluttet, Long, Avsluttet> {
    private var hendelseStateStore: KeyValueStore<Long, Startet>? = null
    private var context: ProcessorContext<Long, Avsluttet>? = null

    override fun init(context: ProcessorContext<Long, Avsluttet>?) {
        super.init(context)
        this.context = context
        hendelseStateStore = context?.getStateStore(hendelseStateStoreName)
    }

    override fun process(record: Record<Long, Avsluttet>?) {
        if (record == null) return
        val store = requireNotNull(hendelseStateStore) { "State store is not initialized" }
        val storeKey = arbeidssoekerIdFun(record.value().identitetsnummer).id
        store.delete(storeKey)
    }
}