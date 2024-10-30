package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SentryRandomTest {

    @Test
    fun `thread local creates a new instance per thread but keeps re-using it for the same thread`() {
        val mainThreadRandom1 = SentryRandom.current()
        val mainThreadRandom2 = SentryRandom.current()
        assertEquals(mainThreadRandom1.toString(), mainThreadRandom2.toString())

        var thread1Random1: Random? = null
        var thread1Random2: Random? = null

        val thread1 = Thread() {
            thread1Random1 = SentryRandom.current()
            thread1Random2 = SentryRandom.current()
        }

        var thread2Random1: Random? = null
        var thread2Random2: Random? = null

        val thread2 = Thread() {
            thread2Random1 = SentryRandom.current()
            thread2Random2 = SentryRandom.current()
        }

        thread1.start()
        thread2.start()
        thread1.join()
        thread2.join()

        assertEquals(thread1Random1.toString(), thread1Random2.toString())
        assertNotEquals(mainThreadRandom1.toString(), thread1Random1.toString())

        assertEquals(thread2Random1.toString(), thread2Random2.toString())
        assertNotEquals(mainThreadRandom1.toString(), thread2Random1.toString())

        val mainThreadRandom3 = SentryRandom.current()
        assertEquals(mainThreadRandom1.toString(), mainThreadRandom3.toString())
    }
}
