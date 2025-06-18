package io.sentry

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.net.URL

class JsonSerializerBenchmarkTests {
    class Fixture {
        fun getJsonSerializer() = JsonSerializer(SentryOptions())
    }

    val fixture = Fixture()

    @Test
    fun benchmarkJson() {
        runBenchmark(fixture.getJsonSerializer())
    }

    private fun runBenchmark(serializer: ISerializer) {
        val sessionJson = sanitizedFile("json/session.json")
        val eventJson = sanitizedFile("json/sentry_event.json")
        val envelopeFileURLs =
            listOf(
                resourceFileURL("envelope-session-start.txt"),
                resourceFileURL("envelope_session.txt"),
                resourceFileURL("envelope-event-attachment.txt"),
                resourceFileURL("envelope-transaction.txt"),
                resourceFileURL("envelope_session_sdkversion.txt"),
                resourceFileURL("envelope-feedback.txt"),
                resourceFileURL("envelope_attachment.txt"),
            )
        simpleMeasureTest(1000) {
            // Deserialize
            val session = serializer.deserialize(StringReader(sessionJson), Session::class.java)
            val event = serializer.deserialize(StringReader(eventJson), SentryEvent::class.java)
            val envelopes =
                envelopeFileURLs.map {
                    serializer.deserializeEnvelope(it.openStream())
                }
            // Serialize
            serializer.serialize(session!!, StringWriter())
            serializer.serialize(event!!, StringWriter())
            envelopes.forEach {
                serializer.serialize(it!!, ByteArrayOutputStream())
            }
        }
    }

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun resourceFileURL(fileName: String): URL = this::class.java.classLoader.getResource(fileName)!!
}

// Source: https://gist.github.com/olegcherr/b62a09aba1bff643a049

/**
 * Iterates provided by [callback] code [ITERATIONS]x[TEST_COUNT] times.
 * Performs warming by iterating [ITERATIONS]x[WARM_COUNT] times.
 */
fun simpleMeasureTest(
    ITERATIONS: Int = 1000,
    TEST_COUNT: Int = 10,
    WARM_COUNT: Int = 2,
    callback: () -> Unit,
) {
    val results = ArrayList<Long>()
    var totalTime = 0L
    var t = 0

    println("$PRINT_REFIX -> go")

    while (++t <= TEST_COUNT + WARM_COUNT) {
        val startTime = System.currentTimeMillis()

        var i = 0
        while (i++ < ITERATIONS) {
            callback()
        }

        if (t <= WARM_COUNT) {
            println("$PRINT_REFIX Warming $t of $WARM_COUNT")
            continue
        }

        val time = System.currentTimeMillis() - startTime
        println(PRINT_REFIX + " " + time.toString() + "ms")

        results.add(time)
        totalTime += time
    }

    results.sort()

    val average = totalTime / TEST_COUNT
    val median = results[results.size / 2]

    println("$PRINT_REFIX -> average=${average}ms / median=${median}ms")
}

/**
 * Used to filter console messages easily
 */
private val PRINT_REFIX = "[TimeTest]"
