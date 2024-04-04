package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.vo.AvsluttetHendelseSerde
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.kafkakeygenerator.KafkaIdAndRecordKeyFunction
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentForenkletStatus
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.vo.HendelseSerde
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced

fun StreamsBuilder.appTopology(
    prometheusRegistry: PrometheusMeterRegistry,
    periodeStateStoreName: String,
    hendelseStateStoreName: String,
    idAndRecordKeyFunction: KafkaIdAndRecordKeyFunction,
    pdlHentForenkletStatus: PdlHentForenkletStatus,
    periodeTopic: String,
    hendelseLoggTopic: String
): Topology {
    stream(hendelseLoggTopic, Consumed.with(Serdes.Long(), HendelseSerde()))
        .filter { _, value -> value is Startet }
        .mapValues { value -> value as Startet }
        .lagreForhaandsGodkjenteHendelser(
            hendelseStateStoreName = hendelseStateStoreName,
            arbeidssoekerIdFun = idAndRecordKeyFunction
        )

    stream(hendelseLoggTopic, Consumed.with(Serdes.Long(), HendelseSerde()))
        .filter { _, value -> value is Avsluttet }
        .mapValues { value -> value as Avsluttet }
        .slettAvsluttetHendelser(
            hendelseStateStoreName = hendelseStateStoreName,
            arbeidssoekerIdFun = idAndRecordKeyFunction,
        )

    stream<Long, Periode>(periodeTopic)
        .lagreEllerSlettPeriode(
            periodeStateStoreName = periodeStateStoreName,
            hendelseStateStoreName = hendelseStateStoreName,
            prometheusMeterRegistry = prometheusRegistry,
            arbeidssoekerIdFun = idAndRecordKeyFunction,
            pdlHentForenkletStatus = pdlHentForenkletStatus
        )
        .to(hendelseLoggTopic, Produced.with(Serdes.Long(), AvsluttetHendelseSerde()))

    return build()
}