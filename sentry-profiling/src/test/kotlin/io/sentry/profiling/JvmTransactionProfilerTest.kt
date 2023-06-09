package io.sentry.profiling

import one.profiler.AsyncProfiler
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// TODO async-profiler crashes on JDK 17.0.7 https://github.com/async-profiler/async-profiler/issues/747 - should we blacklist unsupported versions?
class JvmTransactionProfilerTest {
    @Test
    fun `async profiler`() {
        val profiler = AsyncProfiler.getInstance()
        assertNotNull(profiler.version)

        val profilingRateNs = TimeUnit.SECONDS.toNanos(1) / 101 // 101 Hz

        val jfrFile = File("out/test.jfr")
        print("JFR will be written to: " + jfrFile.absolutePath)
        jfrFile.parentFile.mkdirs()
        jfrFile.delete()

        val startResult = profiler.execute("start,jfr,event=cpu,interval=" + profilingRateNs + ",file=" + jfrFile.absolutePath);
        assertEquals("Profiling started\n", startResult)
        runForMs(300)
        profiler.stop()

        val numSamples = profiler.samples
        assert(numSamples > 0)

        assert(jfrFile.exists())
    }


    private fun runForMs(milliseconds: Long): Long {
        val until = Instant.now().plusMillis(milliseconds)
        var result: Long = 0
        while (Instant.now().isBefore(until)) {
            // Rather arbitrary numbers here, just to get the profiler to capture something.
            result += findPrimeNumber(milliseconds)
        }
        return result
    }

    private fun findPrimeNumber(n: Long): Long {
        var count = 0
        var a: Long = 2
        while (count < n) {
            var b: Long = 2
            var prime = 1 // to check if found a prime
            while (b * b <= a) {
                if (a % b == 0L) {
                    prime = 0
                    break
                }
                b++
            }
            if (prime > 0) {
                count++
            }
            a++
        }
        return --a
    }
}
