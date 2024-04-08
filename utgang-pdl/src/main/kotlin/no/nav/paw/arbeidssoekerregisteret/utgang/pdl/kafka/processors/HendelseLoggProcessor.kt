package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.processors

import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseState
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import java.util.*

fun KStream<Long, Startet>.oppdaterHendelseState(
    hendelseStateStoreName: String,
): KStream<Long, Startet> {
    val processor = {
        HendelseLoggProcessor(hendelseStateStoreName)
    }
    return process(processor, Named.`as`("hendelseLoggProsessor"), hendelseStateStoreName)
}

class HendelseLoggProcessor(
    private val hendelseStateStoreName: String,
) : Processor<Long, Startet, Long, Startet> {
    private var hendelseStateStore: KeyValueStore<UUID, HendelseState>? = null
    private var context: ProcessorContext<Long, Startet>? = null

    override fun init(context: ProcessorContext<Long, Startet>?) {
        super.init(context)
        this.context = context
        hendelseStateStore = context?.getStateStore(hendelseStateStoreName)
    }

    override fun process(record: Record<Long, Startet>?) {
        if (record == null) return
        val store = requireNotNull(hendelseStateStore) { "State store is not initialized" }
        val startetHendelse = record.value()
        val eksisterendeHendelseState = store.get(startetHendelse.hendelseId)
            store.put(record.value().hendelseId, HendelseState(
                brukerId = startetHendelse.id,
                periodeId = startetHendelse.hendelseId,
                recordKey = record.key(),
                identitetsnummer = startetHendelse.identitetsnummer,
                opplysninger = startetHendelse.opplysninger,
                harTilhoerendePeriode = eksisterendeHendelseState?.harTilhoerendePeriode ?: false
            ))
        }
}
