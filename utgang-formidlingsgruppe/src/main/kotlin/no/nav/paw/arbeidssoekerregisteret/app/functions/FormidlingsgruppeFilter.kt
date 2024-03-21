package no.nav.paw.arbeidssoekerregisteret.app.functions

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.tellFilterResultat
import no.nav.paw.arbeidssoekerregisteret.app.vo.GyldigHendelse
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import java.time.Duration
import java.time.Duration.*


fun KStream<Long, GyldigHendelse>.filterePaaAktivePeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry
): KStream<Long, GyldigHendelse> {
    val processor = {
        FormidlingsgruppeFilter(stateStoreName, prometheusMeterRegistry)
    }
    return process(processor, Named.`as`("filterAktivePerioder"), stateStoreName)
}

class FormidlingsgruppeFilter(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry
) : Processor<Long, GyldigHendelse, Long, GyldigHendelse> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, GyldigHendelse>? = null

    override fun init(context: ProcessorContext<Long, GyldigHendelse>?) {
        super.init(context)
        this.context = context
        stateStore = context?.getStateStore(stateStoreName)
    }

    override fun process(record: Record<Long, GyldigHendelse>?) {
        if (record == null) return
        val store = requireNotNull(stateStore) { "State store is not initialized" }
        val ctx = requireNotNull(context) { "Context is not initialized" }
        val hendelse = record.value()
        val periodeStartTime = store.get(hendelse.id)?.startet?.tidspunkt
        val resultat = if (periodeStartTime == null) {
            FilterResultat.INGEN_PERIODE
        } else {
            val requestedStopTime = hendelse.formidlingsgruppeEndret
            val diffStartTilStopp = between(periodeStartTime, requestedStopTime)
            when {
                diffStartTilStopp > ZERO -> FilterResultat.INKLUDER
                diffStartTilStopp < (-8).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_8_
                diffStartTilStopp < (-4).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_4_8H
                diffStartTilStopp < (-2).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_2_4H
                diffStartTilStopp < (-1).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_1_2H
                else -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_0_1H
            }
        }
        prometheusMeterRegistry.tellFilterResultat(resultat)
        if (resultat == FilterResultat.INKLUDER) {
            ctx.forward(record)
        }
    }

}

val Int.timer: Duration get() = ofHours(this.toLong())

enum class FilterResultat {
    INKLUDER,
    INGEN_PERIODE,
    IGNORER_PERIODE_STARTET_ETTER_0_1H,
    IGNORER_PERIODE_STARTET_ETTER_1_2H,
    IGNORER_PERIODE_STARTET_ETTER_2_4H,
    IGNORER_PERIODE_STARTET_ETTER_4_8H,
    IGNORER_PERIODE_STARTET_ETTER_8_,
}
