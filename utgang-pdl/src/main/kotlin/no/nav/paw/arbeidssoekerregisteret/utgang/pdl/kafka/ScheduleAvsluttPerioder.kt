package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.ApplicationInfo
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.PdlHentForenkletStatus
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseState
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.metrics.tellPdlAvsluttetHendelser
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.metrics.tellStatusFraPdlHentPersonBolk
import no.nav.paw.arbeidssokerregisteret.intern.v1.Avsluttet
import no.nav.paw.arbeidssokerregisteret.intern.v1.Hendelse
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Bruker
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.BrukerType
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Metadata
import no.nav.paw.arbeidssokerregisteret.intern.v1.vo.Opplysning
import no.nav.paw.pdl.PdlException
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Folkeregisterpersonstatus
import org.apache.kafka.streams.processor.Cancellable
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

fun scheduleAvsluttPerioder(
    ctx: ProcessorContext<Long, Hendelse>,
    hendelseStateStore: KeyValueStore<UUID, HendelseState>,
    interval: Duration = Duration.ofDays(1),
    pdlHentForenkletStatus: PdlHentForenkletStatus,
    prometheusMeterRegistry: PrometheusMeterRegistry,
): Cancellable = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) {

    val logger = LoggerFactory.getLogger("scheduleAvsluttPerioder")

    hendelseStateStore.all().use { iterator ->
        iterator
            .asSequence()
            .toList()
            .filterNot { it.value.harTilhoerendePeriode && it.value.brukerId == null }
            .chunked(1000) { chunk ->
                val identitetsnummere = chunk.map { it.value.identitetsnummer }

                val pdlForenkletStatus =
                    try {
                        pdlHentForenkletStatus.hentForenkletStatus(
                            identitetsnummere,
                            UUID.randomUUID().toString(),
                            "paw-arbeidssoekerregisteret-utgang-pdl"
                        )
                    } catch (e: PdlException) {
                        logger.error("PDL hentForenkletStatus feiler med: $e", e)
                        return@chunked
                    }

                if (pdlForenkletStatus == null) {
                    logger.error("PDL hentForenkletStatus returnerte null")
                    return@chunked
                }

                pdlForenkletStatus.forEachIndexed { index, result ->
                    prometheusMeterRegistry.tellStatusFraPdlHentPersonBolk(result.code)

                    if (result.code in setOf("bad_request", "not_found")) {
                        return@forEachIndexed
                    }

                    val person = result.person ?: return@forEachIndexed

                    if (person.folkeregisterpersonstatus.any { it.forenkletStatus !== "bosattEtterFolkeregisterloven" }) {
                        val hendelseState = chunk[index].value

                        val opplysningerFraHendelseState = hendelseState?.opplysninger ?: emptySet()

                        val avsluttPeriodeGrunnlag =
                            avsluttPeriodeGrunnlag(person.folkeregisterpersonstatus, opplysningerFraHendelseState)

                        if (avsluttPeriodeGrunnlag.isEmpty()) {
                            return@forEachIndexed
                        }

                        hendelseStateStore.delete(hendelseState.periodeId)

                        val aarsaker =
                            avsluttPeriodeGrunnlag.joinToString(separator = ", ")

                        prometheusMeterRegistry.tellPdlAvsluttetHendelser(aarsaker)

                        val avsluttetHendelse =
                            Avsluttet(
                                hendelseId = hendelseState.periodeId,
                                id = hendelseState.brukerId ?: throw IllegalStateException("BrukerId er null"),
                                identitetsnummer = hendelseState.identitetsnummer,
                                metadata = Metadata(
                                    tidspunkt = Instant.now(),
                                    aarsak = aarsaker,
                                    kilde = "paw-arbeidssoekerregisteret-utgang-pdl",
                                    utfoertAv = Bruker(
                                        type = BrukerType.SYSTEM,
                                        id = ApplicationInfo.id
                                    )
                                )
                            )

                        val record =
                            Record(
                                hendelseState.recordKey,
                                avsluttetHendelse,
                                avsluttetHendelse.metadata.tidspunkt.toEpochMilli()
                            )
                        ctx.forward(record)
                    }
                }
            }
    }
}

fun avsluttPeriodeGrunnlag(
    folkeregisterpersonstatus: List<Folkeregisterpersonstatus>,
    opplysningerFraStartetHendelse: Set<Opplysning>
): Set<Opplysning> {

    val isForhaandsGodkjent = Opplysning.FORHAANDSGODKJENT_AV_ANSATT in opplysningerFraStartetHendelse

    return folkeregisterpersonstatus.mapNotNull { status ->
        when (status.forenkletStatus) {
            "ikkeBosatt" -> Opplysning.IKKE_BOSATT
            "forsvunnet" -> Opplysning.SAVNET
            "doedIFolkeregisteret" -> Opplysning.DOED
            "opphoert" -> Opplysning.OPPHOERT_IDENTITET
            "dNummer" -> Opplysning.DNUMMER
            else -> null
        }
    }
        .filterNot { it in opplysningerFraStartetHendelse && isForhaandsGodkjent }
        .filter { it in negativeOpplysninger }
        .toSet()
}

val negativeOpplysninger = setOf(
    Opplysning.IKKE_BOSATT,
    Opplysning.SAVNET,
    Opplysning.DOED,
    Opplysning.OPPHOERT_IDENTITET,
)