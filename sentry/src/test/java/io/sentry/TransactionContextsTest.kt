package io.sentry

import io.sentry.protocol.App
import io.sentry.protocol.Browser
import io.sentry.protocol.Device
import io.sentry.protocol.Gpu
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryRuntime
import java.lang.IllegalArgumentException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class TransactionContextsTest {

    @Test
    fun `cloning contexts wont have the same references`() {
        val contexts = TransactionContexts()
        contexts.app = App()
        contexts.browser = Browser()
        contexts.device = Device()
        contexts.operatingSystem = OperatingSystem()
        contexts.runtime = SentryRuntime()
        contexts.gpu = Gpu()
        contexts.trace = Trace()

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertNotSame(contexts.app, clone.app)
        assertNotSame(contexts.browser, clone.browser)
        assertNotSame(contexts.device, clone.device)
        assertNotSame(contexts.operatingSystem, clone.operatingSystem)
        assertNotSame(contexts.runtime, clone.runtime)
        assertNotSame(contexts.gpu, clone.gpu)
        assertNotSame(contexts.trace, clone.trace)
    }

    @Test
    fun `cloning contexts will have the same values`() {
        val contexts = TransactionContexts()
        contexts["some-property"] = "some-value"
        contexts.trace = Trace()
        contexts.trace.op = "http"

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertEquals(contexts["some-property"], clone["some-property"])
        assertEquals(contexts.trace.op, clone.trace.op)
    }

    @Test
    fun `creates context from correct traceparent header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromTraceparent("$traceId-$spanId")
        assertEquals(contexts.trace.traceId, traceId)
        assertEquals(contexts.trace.spanId, spanId)
    }

    @Test
    fun `when traceparent header is incorrect throws exception`() {
        val sentryId = SentryId()
        val ex = assertFailsWith<IllegalArgumentException> { TransactionContexts.fromTraceparent("$sentryId") }
        assertEquals("Traceparent header does not conform to expected format: $sentryId", ex.message)
    }
}
