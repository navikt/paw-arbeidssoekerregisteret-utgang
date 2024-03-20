package no.nav.paw.arbeidssoekerregisteret.app

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.functions.filterePaaAktivePeriode
import no.nav.paw.arbeidssoekerregisteret.app.functions.genericProcess
import no.nav.paw.arbeidssoekerregisteret.app.functions.lagreEllerSlettPeriode
import no.nav.paw.arbeidssoekerregisteret.app.functions.mapNonNull
import no.nav.paw.arbeidssoekerregisteret.app.vo.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Repartitioned
import java.time.Instant
import java.util.*

fun StreamsBuilder.appTopology(
    prometheusRegistry: PrometheusMeterRegistry,
    stateStoreName: String,
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    periodeTopic: String,
    formidlingsgrupperTopic: String,
    hendelseLoggTopic: String
): Topology {
    val formidlingsgruppeHendelseSerde = FormidlingsgruppeHendelseSerde()
    stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            stateStoreName = stateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction
        )


    stream(formidlingsgrupperTopic, Consumed.with(Serdes.String(), formidlingsgruppeHendelseSerde))
        .filter { _, value ->
            value.formidlingsgruppe.kode.equals("ISERV", ignoreCase = true)
        }
        .filter { _, value -> value.foedselsnummer != null }
        .map { _, value ->
            // Non null (!!) er trygg så lenge filteret over ikke fjernes
            val (id, newKey) = idAndRecordKeyFunction(value.foedselsnummer!!.foedselsnummer)
            KeyValue(newKey, value.copy(idFraKafkaKeyGenerator = id))
        }
        .repartition(
            Repartitioned
                .numberOfPartitions<Long?, FormidlingsgruppeHendelse?>(partitionCount)
                .withKeySerde(Serdes.Long())
                .withValueSerde(formidlingsgruppeHendelseSerde)
        )
        .filterePaaAktivePeriode(
            stateStoreName,
            prometheusRegistry,
            idAndRecordKeyFunction
        )
        // Non null mapping kan fjernes når vi er sikker på at bare filtrerte hendelser kommer hit
        .mapNonNull("mapNonNullFnr") { formidlingsgruppeHendelse ->
            formidlingsgruppeHendelse.foedselsnummer
                ?.foedselsnummer
                ?.let {
                    Avsluttet(
                        hendelseId = UUID.randomUUID(),
                        id = requireNotNull(formidlingsgruppeHendelse.idFraKafkaKeyGenerator) { "idFraKafkaKeyGenerator is null" },
                        identitetsnummer = it,
                        metadata = Metadata(
                            tidspunkt = Instant.now(),
                            aarsak = formidlingsgruppeHendelse.formidlingsgruppe.kode,
                            kilde = "topic:$formidlingsgrupperTopic",
                            utfoertAv = Bruker(
                                type = BrukerType.SYSTEM,
                                id = ApplicationInfo.id
                            )
                        )
                    )
                }
        }.genericProcess("setRecordTimestamp") { record ->
            forward(record.withTimestamp(record.value().metadata.tidspunkt.toEpochMilli()))
        }.to(hendelseLoggTopic, Produced.with(Serdes.Long(), HendelseSerde()))
    return build()
}