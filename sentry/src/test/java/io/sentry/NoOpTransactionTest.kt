package io.sentry

import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun `getOperation does not return null`() {
        assertNotNull(transaction.operation)
    }

    @Test
    fun `isProfileSampled returns null`() {
        assertNull(transaction.isProfileSampled)
    }

    @Test
    fun `updateEndDate return false`() {
        assertFalse(transaction.updateEndDate(mock()))
    }

    @Test
    fun `startDate return a NanotimeDate`() {
        assertIs<SentryNanotimeDate>(transaction.startDate)
    }
}
