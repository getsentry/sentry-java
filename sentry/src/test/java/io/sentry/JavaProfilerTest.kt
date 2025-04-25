package io.sentry

import io.sentry.protocol.ViewHierarchy
import one.profiler.AsyncProfiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaProfilerTest {

    private class Fixture {
        val contentType = "application/json"
        val filename = "logs.txt"
        val bytes = "content".toByteArray()
        val pathname = "path/to/$filename"
    }

    private val fixture = Fixture()

    @Test
    fun `testprofilerone`() {
        val profiler = AsyncProfiler.getInstance()
        val startResult = profiler.execute("start,jfr,event=wall,alloc,loop=5s,file=test88-%t.jfr");
        println(startResult)

        for (i in 1..20) {
            println(i)
            Thread.sleep(100)
        }

        var endResult = profiler.execute("stop,jfr,file=myNewFile.jfr");
        println(endResult)
    }
}
