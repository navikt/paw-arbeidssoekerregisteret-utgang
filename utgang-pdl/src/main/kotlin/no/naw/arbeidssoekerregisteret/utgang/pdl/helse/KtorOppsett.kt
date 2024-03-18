package no.naw.arbeidssoekerregisteret.utgang.pdl.helse

import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusMeterRegistry

fun initKtor(
    kafkaStreamsMetrics: KafkaStreamsMetrics,
    prometheusRegistry: PrometheusMeterRegistry,
    helse: Helse
): ApplicationEngine {
    return embeddedServer(Netty, port = 8080) {
        install(MicrometerMetrics) {
            registry = prometheusRegistry
            meterBinders = listOf(
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                ProcessorMetrics(),
                kafkaStreamsMetrics
            )
        }
        routing {
            get("/isReady") {
                val status = helse.ready()
                call.respond(status.code, status.message)
            }
            get("/isAlive") {
                val alive = helse.alive()
                call.respond(alive.code, alive.message)
            }
            get("/metrics") {
                call.respond(prometheusRegistry.scrape())
            }
        }
    }
}
