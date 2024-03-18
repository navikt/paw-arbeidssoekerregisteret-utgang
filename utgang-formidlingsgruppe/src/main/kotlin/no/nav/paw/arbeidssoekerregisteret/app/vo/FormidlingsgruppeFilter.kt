package no.nav.paw.arbeidssoekerregisteret.app.vo


import io.micrometer.core.instrument.Tag
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
import java.time.Duration
import java.time.ZoneOffset
import java.time.Duration.*


fun KStream<Long, FormidlingsgruppeHendelse>.filterePaaAktivePeriode(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdfun: KafkaIdAndRecordKeyFunction
): KStream<Long, FormidlingsgruppeHendelse> {
    val processor = {
        FormidlingsgruppeFilter(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdfun)
    }
    return process(processor, Named.`as`("filterAktivePerioder"), stateStoreName)
}

class FormidlingsgruppeFilter(
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
        val periodeStartTime = store.get(storeKey)?.startet?.tidspunkt
        val requestedStopTime = record.value().formidlingsgruppeEndret.toInstant(ZoneOffset.of(ZoneOffset.systemDefault().id))
        val diffStartTilStopp = between(periodeStartTime, requestedStopTime)
        val resultat = when {
            periodeStartTime == null -> FilterResultat.INGEN_PERIODE
            diffStartTilStopp > ZERO -> FilterResultat.INKLUDER
            diffStartTilStopp < (-8).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_8_
            diffStartTilStopp < (-4).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_4_8H
            diffStartTilStopp < (-2).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_2_4H
            diffStartTilStopp < (-1).timer -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_1_2H
            else -> FilterResultat.IGNORER_PERIODE_STARTET_ETTER_0_1H
        }
        prometheusMeterRegistry.counter(
            "paw.arbeidssoekerregisteret.formidlingsgrupper.filter",
            listOf(Tag.of("resultat", resultat.name))
        ).increment()
        if (resultat == FilterResultat.INKLUDER) {
            ctx.forward(record)
        }
    }

}

val Int.timer : Duration get() = ofHours(this.toLong())

enum class FilterResultat {
    INKLUDER,
    INGEN_PERIODE,
    IGNORER_PERIODE_STARTET_ETTER_0_1H,
    IGNORER_PERIODE_STARTET_ETTER_1_2H,
    IGNORER_PERIODE_STARTET_ETTER_2_4H,
    IGNORER_PERIODE_STARTET_ETTER_4_8H,
    IGNORER_PERIODE_STARTET_ETTER_8_,
}

