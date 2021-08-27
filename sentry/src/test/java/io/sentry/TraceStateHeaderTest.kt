package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.SentryId
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class TraceStateHeaderTest {

    @Test
    fun `encodes to base64`() {
        assertEquals("eyJ4IjoiYS3wn5mCLeivu+WGmeaxieWtlyAtIOWtpuS4reaWhyJ9", TraceStateHeader.base64encode("{\"x\":\"a-🙂-读写汉字 - 学中文\"}"))
    }

    @Test
    fun `decode from base64`() {
        assertEquals("{\"x\":\"a-🙂-读写汉字 - 学中文\"}", TraceStateHeader.base64decode("eyJ4IjoiYS3wn5mCLeivu+WGmeaxieWtlyAtIOWtpuS4reaWhyJ9"))
    }

    @Test
    fun `creates header from the trace state`() {
        val traceState = TraceState(SentryId("3367f5196c494acaae85bbbd535379ac"), "key", "env", "release", TraceState.TraceStateUser("id", "segment"), "transaction name")
        val serializer = mock<ISerializer>()
        whenever(serializer.serialize(eq(traceState), any())).thenAnswer { (it.arguments[1] as StringWriter).write("""{"trace_id":"3367f5196c494acaae85bbbd535379ac"}""") }
        val header = TraceStateHeader.fromTraceState(traceState, serializer, mock())
        assertEquals("eyJ0cmFjZV9pZCI6IjMzNjdmNTE5NmM0OTRhY2FhZTg1YmJiZDUzNTM3OWFjIn0", header.value)
        assertEquals("tracestate", header.name)
    }
}
