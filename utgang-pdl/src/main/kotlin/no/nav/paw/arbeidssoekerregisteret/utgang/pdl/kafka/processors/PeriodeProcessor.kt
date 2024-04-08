package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.processors

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentForenkletStatus
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.scheduleAvsluttPerioder
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseState
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import java.time.Duration
import java.util.UUID

fun KStream<Long, Periode>.oppdaterHendelseState(
    hendelseStateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    pdlHentForenkletStatus: PdlHentForenkletStatus
): KStream<Long, Hendelse> {
    val processor = {
        PeriodeProcessor(
            hendelseStateStoreName,
            prometheusMeterRegistry,
            pdlHentForenkletStatus
        )
    }
    return process(processor, Named.`as`("periodeProsessor"), hendelseStateStoreName)
}

class PeriodeProcessor(
    private val hendelseStateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val pdlHentForenkletStatus: PdlHentForenkletStatus
) : Processor<Long, Periode, Long, Hendelse> {
    private var hendelseStateStore: KeyValueStore<UUID, HendelseState>? = null
    private var context: ProcessorContext<Long, Hendelse>? = null

    override fun init(context: ProcessorContext<Long, Hendelse>?) {
        super.init(context)
        this.context = context
        hendelseStateStore = context?.getStateStore(hendelseStateStoreName)
        scheduleAvsluttPerioder(
            requireNotNull(context),
            requireNotNull(hendelseStateStore),
            Duration.ofDays(1),
            pdlHentForenkletStatus,
            prometheusMeterRegistry
        )
    }

    override fun process(record: Record<Long, Periode>?) {
        if (record == null) return
        val hendelseStore = requireNotNull(hendelseStateStore) { "State store is not initialized" }
        if (record.value().avsluttet != null) {
            hendelseStore.delete(record.value().id)
            return
        }
        val hendelseState = hendelseStore.get(record.value().id)
        if (hendelseState == null) {
            hendelseStore.put(
                record.value().id, HendelseState(
                    brukerId = null,
                    periodeId = record.value().id,
                    recordKey = record.key(),
                    identitetsnummer = record.value().identitetsnummer,
                    opplysninger = emptySet(),
                    harTilhoerendePeriode = true
                )
            )
        } else {
            hendelseState.harTilhoerendePeriode = true
            hendelseStore.put(record.value().id, hendelseState)
        }
    }
}
