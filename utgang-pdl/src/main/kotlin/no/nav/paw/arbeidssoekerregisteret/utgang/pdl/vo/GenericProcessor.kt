package no.nav.paw.arbeidssoekerregisteret.app.vo


import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record

fun <K, V> KStream<K, V>.genericProcess(
    name: String,
    vararg stateStoreNames: String,
    function: ProcessorContext<K, V>.(Record<K, V>) -> Unit
): KStream<K, V> {
    val processor = {
        GenericProcessor(function)
    }
    return process(processor, Named.`as`(name), *stateStoreNames)
}

class GenericProcessor<K,V>(
    private val function: ProcessorContext<K, V>.(Record<K, V>) -> Unit
) : Processor<K, V, K, V> {
    private var context: ProcessorContext<K, V>? = null

    override fun init(context: ProcessorContext<K, V>?) {
        super.init(context)
        this.context = context
    }

    override fun process(record: Record<K, V>?) {
        if (record == null) return
        val ctx = requireNotNull(context) { "Context is not initialized" }
        with(ctx){function(record)}
    }
}
