package no.nav.paw.arbeidssoekerregisteret.utgang.pdl.health

import io.ktor.http.HttpStatusCode
import org.apache.kafka.streams.KafkaStreams

class Health(private val kafkaStreams: KafkaStreams) {
    fun alive(): Status {
        val state = kafkaStreams.state()
        val httpStatusCode = when (state) {
            KafkaStreams.State.CREATED -> HttpStatusCode.OK
            KafkaStreams.State.REBALANCING -> HttpStatusCode.OK
            KafkaStreams.State.RUNNING -> HttpStatusCode.OK
            KafkaStreams.State.PENDING_SHUTDOWN -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.NOT_RUNNING -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.PENDING_ERROR -> HttpStatusCode.InternalServerError
            KafkaStreams.State.ERROR -> HttpStatusCode.InternalServerError
            null -> HttpStatusCode.InternalServerError
        }
        return status(httpStatusCode, state)
    }

    fun ready(): Status {
        val state = kafkaStreams.state()
        val httpStatusCode = when (state) {
            KafkaStreams.State.RUNNING -> HttpStatusCode.OK
            KafkaStreams.State.CREATED -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.REBALANCING -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.PENDING_SHUTDOWN -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.NOT_RUNNING -> HttpStatusCode.ServiceUnavailable
            KafkaStreams.State.PENDING_ERROR -> HttpStatusCode.InternalServerError
            KafkaStreams.State.ERROR -> HttpStatusCode.InternalServerError
            null -> HttpStatusCode.InternalServerError
        }
        return status(httpStatusCode, state)
    }

    private fun status(kode: HttpStatusCode, kafkaStreamsTilstand: KafkaStreams.State?): Status =
        Status(kode, "KafkaStreams tilstand: '${kafkaStreamsTilstand?.name}'")
}
