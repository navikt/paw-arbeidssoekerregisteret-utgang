package no.naw.arbeidssoekerregisteret.utgang.pdl

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.app.vo.*
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import no.nav.paw.pdl.PdlClient
import no.naw.arbeidssoekerregisteret.utgang.pdl.clients.KafkaIdAndRecordKeyFunction
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Produced
import java.time.Instant
import java.util.*

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
        .map { _, value ->
            val (id, newKey) = idAndRecordKeyFunction(value.identitetsnummer)

            KeyValue(
                newKey,
                Avsluttet(
                    hendelseId = UUID.randomUUID(),
                    id = id,
                    identitetsnummer = value.identitetsnummer,
                    metadata = Metadata(
                        tidspunkt = Instant.now(),
                        aarsak = "PDL 'forenkletStatus' endret til ....",
                        kilde = "PDL-utgang",
                        utfoertAv = Bruker(
                            type = BrukerType.SYSTEM,
                            id = ApplicationInfo.id
                        )
                    )
                )
            )
        }.to(hendelseLoggTopic, Produced.with(Serdes.Long(), HendelseSerde()))

    return build()
}