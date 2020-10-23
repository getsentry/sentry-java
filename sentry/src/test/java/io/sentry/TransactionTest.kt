package io.sentry

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionTest {

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = Transaction()
        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = Transaction()
        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = Transaction()
        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = Transaction()
        transaction.finish()
        assertNotNull(transaction.timestamp)
    }
}
