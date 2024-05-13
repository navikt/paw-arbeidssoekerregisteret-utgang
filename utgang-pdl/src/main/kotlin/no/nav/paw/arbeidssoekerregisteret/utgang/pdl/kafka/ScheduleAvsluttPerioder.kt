package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka

import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.ApplicationInfo
import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.clients.pdl.BEHANDLINGSNUMMER
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
import no.nav.paw.pdl.graphql.generated.hentforenkletstatusbolk.HentPersonBolkResult
import org.apache.kafka.streams.processor.Cancellable
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.KeyValue
import org.slf4j.Logger
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
            .filterValidHendelseStates()
            .chunked(1000) { chunk ->
                val identitetsnummere = chunk.map { it.value.identitetsnummer }

                val pdlResults = hentForenkletStatus(identitetsnummere, pdlHentForenkletStatus)
                if (pdlResults == null) {
                    logger.error("PDL hentForenkletStatus returnerte null")
                    return@chunked
                }

                pdlResults.processResults(
                    chunk,
                    hendelseStateStore,
                    ctx,
                    prometheusMeterRegistry,
                    logger
                )
            }
    }
}

private fun List<HentPersonBolkResult>.processResults(
    chunk: List<KeyValue<UUID, HendelseState>>,
    hendelseStateStore: KeyValueStore<UUID, HendelseState>,
    ctx: ProcessorContext<Long, Hendelse>,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    logger: Logger
) = this.forEachIndexed { _, result ->
    prometheusMeterRegistry.tellStatusFraPdlHentPersonBolk(result.code)

    if (result.code in pdlErrorResponses) {
        logger.error("Feil ved henting av forenklet status fra PDL: ${result.code}")
        return@forEachIndexed
    }

    val (folkeregisterpersonstatus, hendelseState) = hentPersonStatusOgHendelseState(result, chunk)
        ?: return@forEachIndexed

    if (folkeregisterpersonstatus.erBosattEtterFolkeregisterloven) {
        oppdaterHendelseStateOpplysninger(hendelseState, hendelseStateStore)
        return@forEachIndexed
    }

    logger.info("Vurderer grunnlag for utmelding av person med folkeregisterstatus: $folkeregisterpersonstatus og opplysninger fra hendelse: ${hendelseState.opplysninger}")

    val aarsak = folkeregisterpersonstatus
        .filterAvsluttPeriodeGrunnlag(hendelseState.opplysninger)
        .ifEmpty { return@forEachIndexed }
        .toAarsak()

    val avsluttetHendelse = genererAvsluttetHendelseRecord(hendelseState, aarsak)

    logger.info("Sender avsluttet hendelse med aarsak: $aarsak")

    ctx.forward(avsluttetHendelse)
        .also {
            prometheusMeterRegistry.tellPdlAvsluttetHendelser(aarsak)
            hendelseStateStore.delete(hendelseState.periodeId)
        }
}

private fun hentPersonStatusOgHendelseState(
    result: HentPersonBolkResult,
    chunk: List<KeyValue<UUID, HendelseState>>
): Pair<List<Folkeregisterpersonstatus>, HendelseState>? {
    val person = result.person ?: return null
    val hendelseState = chunk.find { it.value.identitetsnummer == result.ident }
        ?.value ?: return null

    return Pair(person.folkeregisterpersonstatus, hendelseState)
}

private fun oppdaterHendelseStateOpplysninger(
    hendelseState: HendelseState,
    hendelseStateStore: KeyValueStore<UUID, HendelseState>
) {
    if (Opplysning.FORHAANDSGODKJENT_AV_ANSATT in hendelseState.opplysninger && hendelseState.opplysninger.any { it in negativeOpplysninger }) {
        val oppdatertHendelseState = hendelseState.copy(
            opplysninger = hendelseState.opplysninger
                .filterNot { it == Opplysning.FORHAANDSGODKJENT_AV_ANSATT || it in negativeOpplysninger }
                .toSet()
        )
        hendelseStateStore.put(hendelseState.periodeId, oppdatertHendelseState)
    }
}

private fun hentForenkletStatus(
    identitetsnummere: List<String>,
    pdlHentForenkletStatus: PdlHentForenkletStatus,
): List<HentPersonBolkResult>? {
    return pdlHentForenkletStatus.hentForenkletStatus(
        identitetsnummere,
        UUID.randomUUID().toString(),
        "paw-arbeidssoekerregisteret-utgang-pdl",
    )
}

private fun List<KeyValue<UUID, HendelseState>>.filterValidHendelseStates(): List<KeyValue<UUID, HendelseState>> =
    this.filter { entry ->
        val hendelseState = entry.value
        hendelseState.harTilhoerendePeriode && hendelseState.brukerId != null
    }

fun List<Folkeregisterpersonstatus>.filterAvsluttPeriodeGrunnlag(
    opplysningerFraStartetHendelse: Set<Opplysning>
): Set<Opplysning> {
    val isForhaandsGodkjent = Opplysning.FORHAANDSGODKJENT_AV_ANSATT in opplysningerFraStartetHendelse

    return this.asSequence()
        .mapNotNull { status -> statusToOpplysningMap[status.forenkletStatus] }
        .filter { it in negativeOpplysninger }
        // Opplysning FORHAANDSGODKJENT_AV_ANSATT + DOED/SAVNET/IKKE_BOSATT/OPPHOERT_IDENTITET skal overstyre tilsvarende forenkletStatus fra PDL
        .filterNot { it in opplysningerFraStartetHendelse && isForhaandsGodkjent }
        .toSet()
}

private fun Set<Opplysning>.toAarsak(): String =
    this.joinToString(separator = ", ") {
        when (it) {
            Opplysning.DOED -> "Personen er doed"
            Opplysning.SAVNET -> "Personen er savnet"
            Opplysning.IKKE_BOSATT -> "Personen er ikke bosatt etter folkeregisterloven"
            Opplysning.OPPHOERT_IDENTITET -> "Personen har opphoert identitet"
            else -> it.name
        }
    }

private val negativeOpplysninger = setOf(
    Opplysning.IKKE_BOSATT,
    Opplysning.SAVNET,
    Opplysning.DOED,
    Opplysning.OPPHOERT_IDENTITET,
)

private val statusToOpplysningMap = mapOf(
    "ikkeBosatt" to Opplysning.IKKE_BOSATT,
    "forsvunnet" to Opplysning.SAVNET,
    "doedIFolkeregisteret" to Opplysning.DOED,
    "opphoert" to Opplysning.OPPHOERT_IDENTITET,
    "dNummer" to Opplysning.DNUMMER
)

private val List<Folkeregisterpersonstatus>.erBosattEtterFolkeregisterloven
    get(): Boolean =
        this.any { it.forenkletStatus == "bosattEtterFolkeregisterloven" }

private val pdlErrorResponses = setOf(
    "bad_request",
    "not_found"
)

private fun genererAvsluttetHendelseRecord(
    hendelseState: HendelseState,
    aarsak: String,
): Record<Long, Avsluttet> = Record(
    hendelseState.recordKey,
    Avsluttet(
        hendelseId = hendelseState.periodeId,
        id = hendelseState.brukerId ?: throw IllegalStateException("BrukerId er null"),
        identitetsnummer = hendelseState.identitetsnummer,
        metadata = Metadata(
            tidspunkt = Instant.now(),
            aarsak = aarsak,
            kilde = "paw-arbeidssoekerregisteret-utgang-pdl",
            utfoertAv = Bruker(
                type = BrukerType.SYSTEM,
                id = ApplicationInfo.id
            )
        )
    ),
    Instant.now().toEpochMilli()
)
