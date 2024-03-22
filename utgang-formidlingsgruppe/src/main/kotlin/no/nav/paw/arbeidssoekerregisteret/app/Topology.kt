package no.nav.paw.arbeidssoekerregisteret.app

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.functions.filterePaaAktivePeriode
import no.nav.paw.arbeidssoekerregisteret.app.functions.genericProcess
import no.nav.paw.arbeidssoekerregisteret.app.functions.lagreEllerSlettPeriode
import no.nav.paw.arbeidssoekerregisteret.app.functions.mapNonNull
import no.nav.paw.arbeidssoekerregisteret.app.vo.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Repartitioned

fun StreamsBuilder.appTopology(
    prometheusRegistry: PrometheusMeterRegistry,
    stateStoreName: String,
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    periodeTopic: String,
    formidlingsgrupperTopic: String,
    hendelseLoggTopic: String
): Topology {
    val arenaFormidlingsgruppeSerde = ArenaFormidlingsgruppeSerde()
    stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            stateStoreName = stateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction
        )

    stream(formidlingsgrupperTopic, Consumed.with(Serdes.String(), arenaFormidlingsgruppeSerde))
        .mapNonNull("mapTilGyldigHendelse") { formidlingsgruppeHendelse ->
            formidlingsgruppeHendelse.validValuesOrNull()
                .also { gyldigeVerdier ->
                    if (gyldigeVerdier == null) {
                        prometheusRegistry.tellUgyldigHendelse()
                    }
                }
        }
        .filter { _, (_, formidlingsgruppe, _) ->
            formidlingsgruppe.kode.equals("ISERV", ignoreCase = true).also { isServ ->
                if (!isServ) {
                    prometheusRegistry.tellIgnorertGrunnetFormidlingsgruppe(formidlingsgruppe)
                }
            }
        }
        .map { _, (foedselsnummer, formidlingsgruppe, tidspunkt) ->
            val (id, newKey) = idAndRecordKeyFunction(foedselsnummer.foedselsnummer)
            KeyValue(
                newKey, GyldigHendelse(
                    id = id,
                    foedselsnummer = foedselsnummer,
                    formidlingsgruppe = formidlingsgruppe,
                    formidlingsgruppeEndret = tidspunkt
                )
            )
        }
        .repartition(
            Repartitioned
                .numberOfPartitions<Long?, GyldigHendelse?>(partitionCount)
                .withKeySerde(Serdes.Long())
                .withValueSerde(GyldigHendelseSerde())
        )
        .filterePaaAktivePeriode(
            stateStoreName,
            prometheusRegistry
        )
        .mapValues { _, hendelse -> avsluttet(formidlingsgrupperTopic, hendelse) }
        .genericProcess("setRecordTimestamp") { record ->
            forward(record.withTimestamp(record.value().metadata.tidspunkt.toEpochMilli()))
        }.to(hendelseLoggTopic, Produced.with(Serdes.Long(), AvsluttetSerde()))
    return build()
}