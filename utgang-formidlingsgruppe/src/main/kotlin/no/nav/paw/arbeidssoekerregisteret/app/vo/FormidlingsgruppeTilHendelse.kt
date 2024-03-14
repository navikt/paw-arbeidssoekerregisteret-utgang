package no.nav.paw.arbeidssoekerregisteret.app.vo


import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.ApplicationInfo
import no.nav.paw.arbeidssoekerregisteret.app.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata as InternMetadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.util.*


fun KStream<Long, FormidlingsgruppeHendelse>.mapTilHendelser(
    stateStoreName: String,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    arbeidssoekerIdfun: KafkaIdAndRecordKeyFunction
): KStream<Long, Hendelse> {
    val processor = {
        FormidlingsgruppeTilHendelse(stateStoreName, prometheusMeterRegistry, arbeidssoekerIdfun)
    }
    return process(processor, Named.`as`("periodeProsessor"), stateStoreName)
}

class FormidlingsgruppeTilHendelse(
    private val stateStoreName: String,
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    private val arbeidssoekerIdFun: KafkaIdAndRecordKeyFunction
) : Processor<Long, FormidlingsgruppeHendelse, Long, Hendelse> {
    private var stateStore: KeyValueStore<Long, Periode>? = null
    private var context: ProcessorContext<Long, Hendelse>? = null
    private val logger = LoggerFactory.getLogger("applicationTopology")

    override fun init(context: ProcessorContext<Long, Hendelse>?) {
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

        val hendelse = Avsluttet(
            hendelseId = UUID.randomUUID(),
            id = storeKey,
            identitetsnummer = record.value().foedselsnummer.foedselsnummer,
            metadata = InternMetadata(
                tidspunkt = Instant.now(),
                aarsak = "Formidlingsgruppe endret til ${record.value().formidlingsgruppe.kode}",
                kilde = "Arena formidlingsgruppetopic",
                utfoertAv = Bruker(
                    type = BrukerType.SYSTEM,
                    id = ApplicationInfo.id
                )
            )
        )
        ctx.forward(record.withValue(hendelse).withTimestamp(hendelse.metadata.tidspunkt.toEpochMilli()))
    }
}
