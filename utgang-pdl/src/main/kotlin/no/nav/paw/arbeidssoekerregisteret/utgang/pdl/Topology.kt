package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.vo.HendelseSerde
import no.nav.paw.arbeidssoekerregisteret.app.vo.lagreEllerSlettPeriode
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.pdl.PdlClient
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.KafkaIdAndRecordKeyFunction
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Produced

fun StreamsBuilder.appTopology(
    prometheusRegistry: PrometheusMeterRegistry,
    stateStoreName: String,
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    pdlClient: PdlClient,
    periodeTopic: String,
    hendelseLoggTopic: String
): Topology {
    stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            stateStoreName = stateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction,
            pdlClient = pdlClient
        )
        .to(hendelseLoggTopic, Produced.with(Serdes.Long(), HendelseSerde()))

    return build()
}