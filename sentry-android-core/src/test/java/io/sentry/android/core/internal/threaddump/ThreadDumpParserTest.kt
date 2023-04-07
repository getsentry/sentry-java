package io.sentry.android.core.internal.threaddump

import java.io.File
import kotlin.test.Test

class ThreadDumpParserTest {

    @Test
    fun `test`() {
        val lines = Lines.readLines(File("src/test/resources/thread_dump.txt"))
        val parser = ThreadDumpParser()
        val dump = parser.parse(lines)

    }
}
