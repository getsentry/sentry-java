package io.sentry

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
}
