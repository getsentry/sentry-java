package io.sentry

import kotlin.test.Test
import kotlin.test.assertNotNull

class NoOpTransactionTest {

    private val transaction = NoOpTransaction.getInstance()

    @Test
    fun `startChild does not return null`() {
        assertNotNull(transaction.startChild("op"))
        assertNotNull(transaction.startChild("op", "desc"))
    }

    @Test
    fun `getSpanContext does not return null`() {
        assertNotNull(transaction.spanContext)
    }
}
