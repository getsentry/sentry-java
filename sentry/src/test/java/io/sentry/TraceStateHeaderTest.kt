package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class TraceStateHeaderTest {

    @Test
    fun `converts to header friendly base64 string`() {
        assertEquals("eyJ4IjoiYS3wn5iQLeivu%2BWGmeaxieWtlyAtIOWtpuS4reaWhyJ9%0A", TraceStateHeader.toHttpHeaderFriendlyBase64("{\"x\":\"a-😐-读写汉字 - 学中文\"}"))
    }
}
