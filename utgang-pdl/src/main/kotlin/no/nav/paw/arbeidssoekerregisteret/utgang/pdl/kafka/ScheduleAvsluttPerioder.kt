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
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Folkeregisterpersonstatus
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.Person
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
            .filter { it.value.harTilhoerendePeriode && it.value.brukerId != null }
            .chunked(1000) { chunk ->
                val identitetsnummere = chunk.map { it.value.identitetsnummer }

                val pdlForenkletStatus = pdlHentForenkletStatus.hentForenkletStatus(
                    identitetsnummere,
                    UUID.randomUUID().toString(),
                    "paw-arbeidssoekerregisteret-utgang-pdl"
                )

                if (pdlForenkletStatus == null) {
                    logger.error("PDL hentForenkletStatus returnerte null")
                    return@chunked
                }


                pdlForenkletStatus.forEachIndexed { index, result ->
                    prometheusMeterRegistry.tellStatusFraPdlHentPersonBolk(result.code)

                    val hendelseState = chunk[index].value

                    logger.info("Vurderer hendelseState: $hendelseState mot resultat: ${result.person}")

                    val person = result.person
                    if (
                        result.code in pdlErrorResponses
                        || person == null
                        || person.erBosattEtterFolkeregisterloven
                        || hendelseState == null
                    ) {
                        return@forEachIndexed
                    }

                    logger.info("Avslutter periode basert på hendelseState $hendelseState og person $person")

                    val avsluttetHendelse =
                        getAvsluttetHendelseForPerson(person, hendelseState, prometheusMeterRegistry)
                            ?: return@forEachIndexed

                    hendelseStateStore.delete(hendelseState.periodeId)

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

fun avsluttPeriodeGrunnlag(
    folkeregisterpersonstatus: List<Folkeregisterpersonstatus>,
    opplysningerFraStartetHendelse: Set<Opplysning>
): Set<Opplysning> {
    val isForhaandsGodkjent = Opplysning.FORHAANDSGODKJENT_AV_ANSATT in opplysningerFraStartetHendelse

    return folkeregisterpersonstatus.asSequence()
        .mapNotNull { status -> statusToOpplysningMap[status.forenkletStatus] }
        .filter { it in negativeOpplysninger }
        .filterNot { it in opplysningerFraStartetHendelse && isForhaandsGodkjent }
        .toSet()
}

val negativeOpplysninger = setOf(
    Opplysning.IKKE_BOSATT,
    Opplysning.SAVNET,
    Opplysning.DOED,
    Opplysning.OPPHOERT_IDENTITET,
)

val statusToOpplysningMap = mapOf(
    "ikkeBosatt" to Opplysning.IKKE_BOSATT,
    "forsvunnet" to Opplysning.SAVNET,
    "doedIFolkeregisteret" to Opplysning.DOED,
    "opphoert" to Opplysning.OPPHOERT_IDENTITET,
    "dNummer" to Opplysning.DNUMMER
)

private val Person.erBosattEtterFolkeregisterloven
    get(): Boolean =
        this.folkeregisterpersonstatus.any { it.forenkletStatus == "bosattEtterFolkeregisterloven" }

private val pdlErrorResponses = setOf(
    "bad_request",
    "not_found"
)

private fun getAvsluttetHendelseForPerson(
    person: Person,
    hendelseState: HendelseState,
    prometheusMeterRegistry: PrometheusMeterRegistry
): Avsluttet? {
    val opplysningerFraHendelseState = hendelseState.opplysninger

    val avsluttPeriodeGrunnlag =
        avsluttPeriodeGrunnlag(person.folkeregisterpersonstatus, opplysningerFraHendelseState)

    if (avsluttPeriodeGrunnlag.isEmpty()) {
        return null
    }

    val aarsaker = avsluttPeriodeGrunnlag.joinToString(separator = ", ")

    prometheusMeterRegistry.tellPdlAvsluttetHendelser(aarsaker)

    return Avsluttet(
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
}