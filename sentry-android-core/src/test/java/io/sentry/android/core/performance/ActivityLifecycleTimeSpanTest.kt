package io.sentry.android.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityLifecycleTimeSpanTest {
    @Test
    fun `init does not auto-start the spans`() {
        val span = ActivityLifecycleTimeSpan()

        assertTrue(span.onCreate.hasNotStarted())
        assertTrue(span.onStart.hasNotStarted())
    }

    @Test
    fun `spans are compareable`() {
        // given some spans
        val spanA = ActivityLifecycleTimeSpan()
        spanA.onCreate.setStartedAt(1)

        val spanB = ActivityLifecycleTimeSpan()
        spanB.onCreate.setStartedAt(2)

        val spanC = ActivityLifecycleTimeSpan()
        spanC.onCreate.setStartedAt(3)

        // when put into an list out of order
        // then sorted
        val spans = listOf(spanB, spanC, spanA).sorted()

        // puts them back in order
        assertEquals(spanA, spans[0])
        assertEquals(spanB, spans[1])
        assertEquals(spanC, spans[2])
    }
}
