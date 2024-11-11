package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SentryRandomTest {

    @Test
    fun `thread local creates a new instance per thread but keeps re-using it for the same thread`() {
        val mainThreadRandom1 = SentryRandom.current()
        val mainThreadRandom2 = SentryRandom.current()
        assertSame(mainThreadRandom1, mainThreadRandom2)

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

        assertSame(thread1Random1, thread1Random2)
        assertNotSame(mainThreadRandom1, thread1Random1)

        assertSame(thread2Random1, thread2Random2)
        assertNotSame(mainThreadRandom1, thread2Random1)

        val mainThreadRandom3 = SentryRandom.current()
        assertSame(mainThreadRandom1, mainThreadRandom3)
    }
}
