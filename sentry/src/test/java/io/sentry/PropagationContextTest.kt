package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropagationContextTest {
    @Test
    fun `freezes baggage with sentry values`() {
        val propagationContext =
            PropagationContext.fromHeaders(
                NoOpLogger.getInstance(),
                "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1",
                "sentry-trace_id=a,sentry-transaction=sentryTransaction",
            )
        assertFalse(propagationContext.baggage.isMutable)
        assertTrue(propagationContext.baggage.isShouldFreeze)
    }

    @Test
    fun `does not freeze baggage without sentry values`() {
        val propagationContext =
            PropagationContext.fromHeaders(
                NoOpLogger.getInstance(),
                "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1",
                "a=b",
            )
        assertTrue(propagationContext.baggage.isMutable)
        assertFalse(propagationContext.baggage.isShouldFreeze)
    }

    @Test
    fun `creates new baggage if none passed`() {
        val propagationContext =
            PropagationContext.fromHeaders(
                NoOpLogger.getInstance(),
                "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1",
                null as? String?,
            )
        assertNotNull(propagationContext.baggage)
        assertTrue(propagationContext.baggage.isMutable)
        assertFalse(propagationContext.baggage.isShouldFreeze)
    }
}
