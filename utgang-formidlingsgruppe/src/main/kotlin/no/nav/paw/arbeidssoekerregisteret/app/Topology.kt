package no.nav.paw.arbeidssoekerregisteret.app

import io.micrometer.prometheus.PrometheusMeterRegistry
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
    stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            stateStoreName = stateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction
        )

    stream(formidlingsgrupperTopic, Consumed.with(Serdes.String(), FormidlingsgruppeHendelseSerde()))
        .filter { _, value ->
            value.formidlingsgruppe.kode.equals("ISERV", ignoreCase = true)
        }
        .map { _, value ->
            val (id, newKey) = idAndRecordKeyFunction(value.foedselsnummer.foedselsnummer)
            KeyValue(newKey, value.copy(idFraKafkaKeyGenerator = id))
        }
        .repartition(Repartitioned.numberOfPartitions(partitionCount))
        .filterePaaAktivePeriode(
            stateStoreName,
            prometheusRegistry,
            idAndRecordKeyFunction
        )
        .mapValues { _, value ->
            Avsluttet(
                hendelseId = UUID.randomUUID(),
                id = requireNotNull(value.idFraKafkaKeyGenerator) { "idFraKafkaKeyGenerator is null" },
                identitetsnummer = value.foedselsnummer.foedselsnummer,
                metadata = Metadata(
                    tidspunkt = Instant.now(),
                    aarsak = "Formidlingsgruppe endret til ${value.formidlingsgruppe.kode}",
                    kilde = "Arena formidlingsgruppetopic",
                    utfoertAv = Bruker(
                        type = BrukerType.SYSTEM,
                        id = ApplicationInfo.id
                    )
                )
            )
        }.genericProcess("setRecordTimestamp") { record ->
            record.withTimestamp(record.value().metadata.tidspunkt.toEpochMilli())
        }.to(hendelseLoggTopic, Produced.with(Serdes.Long(), HendelseSerde()))

    return build()
}