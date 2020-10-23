package io.sentry

import kotlin.test.Test
import kotlin.test.assertNotNull

class TransactionContextsTest {

    @Test
    fun `when created with default constructor, creates the trace`() {
        val contexts = TransactionContexts()
        assertNotNull(contexts.trace)
    }
}
