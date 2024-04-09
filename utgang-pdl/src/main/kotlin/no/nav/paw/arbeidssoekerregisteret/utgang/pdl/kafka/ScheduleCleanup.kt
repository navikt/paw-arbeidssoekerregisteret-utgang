package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka

import no.nav.paw.arbeidssoekerregisteret.utgang.pdl.kafka.serdes.HendelseState
import no.nav.paw.arbeidssokerregisteret.intern.v1.Startet
import org.apache.kafka.streams.processor.Cancellable
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

fun scheduleCleanup(
    ctx: ProcessorContext<Long, Startet>,
    stateStore: KeyValueStore<UUID, HendelseState>,
    interval: Duration = Duration.ofDays(30)
): Cancellable = ctx.schedule(interval, PunctuationType.WALL_CLOCK_TIME) { time ->
    val logger = LoggerFactory.getLogger("scheduleCleanup")
    try {
        val currentTime = Instant.ofEpochMilli(time)

        stateStore.all().asSequence().filterNot { it.value.harTilhoerendePeriode }.forEach { keyValue ->
            val value = keyValue.value
            if (value.isOutdated(currentTime)) {
                logger.debug("Sletter hendelse uten tilhørende periode med key: {} og hendelse id; {}", keyValue.key, value.periodeId)
                stateStore.delete(keyValue.key)
            }
        }
    } catch (e: Exception) {
        logger.error("Feil ved opprydding av hendelse state store: $e", e)
        throw e
    }
}

private fun HendelseState.isOutdated(currentTime: Instant): Boolean =
    currentTime.isAfter(this.startetTidspunkt.plus(Duration.ofDays(30)))
