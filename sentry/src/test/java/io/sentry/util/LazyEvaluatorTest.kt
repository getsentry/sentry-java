package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LazyEvaluatorTest {

    class Fixture {
        var count = 0

        fun getSut(): LazyEvaluator<Int> {
            count = 0
            return LazyEvaluator<Int> { ++count }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `does not evaluate on instantiation`() {
        fixture.getSut()
        assertEquals(0, fixture.count)
    }

    @Test
    fun `evaluator is called on getValue`() {
        val evaluator = fixture.getSut()
        assertEquals(0, fixture.count)
        assertEquals(1, evaluator.value)
        assertEquals(1, fixture.count)
    }

    @Test
    fun `evaluates only once`() {
        val evaluator = fixture.getSut()
        assertEquals(0, fixture.count)
        assertEquals(1, evaluator.value)
        assertEquals(1, evaluator.value)
        assertEquals(1, fixture.count)
    }

    @Test
    fun `evaluates again after resetValue`() {
        val evaluator = fixture.getSut()
        assertEquals(0, fixture.count)
        assertEquals(1, evaluator.value)
        assertEquals(1, evaluator.value)
        assertEquals(1, fixture.count)
        // Evaluate again, only once
        evaluator.resetValue()
        assertEquals(2, evaluator.value)
        assertEquals(2, evaluator.value)
        assertEquals(2, fixture.count)
    }
}
