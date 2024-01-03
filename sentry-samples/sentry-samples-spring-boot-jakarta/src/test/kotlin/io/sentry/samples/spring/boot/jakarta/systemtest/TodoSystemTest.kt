package io.sentry.samples.spring.boot.jakarta.systemtest

import io.sentry.samples.spring.boot.jakarta.systemtest.util.TestHelper
import org.junit.Before
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodoSystemTest {

    lateinit var testHelper: TestHelper

    @Before
    fun setup() {
        testHelper = TestHelper("http://localhost:8080")
    }

    @Test
    fun `get todo works`() {
        val envelopeCountBefore = testHelper.sentryClient.getEnvelopeCount()
        println(envelopeCountBefore)

        val restClient = testHelper.restClient
        restClient.getTodo(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        Thread.sleep(1000)

        val envelopeCountAfter = testHelper.sentryClient.getEnvelopeCount()
        println(envelopeCountAfter)
        assertTrue(envelopeCountAfter.envelopes!! > envelopeCountBefore.envelopes!!)
    }

    @Test
    fun `get todo webclient works`() {
        val envelopeCountBefore = testHelper.sentryClient.getEnvelopeCount()
        println(envelopeCountBefore)

        val restClient = testHelper.restClient
        restClient.getTodoWebclient(1L)
        assertEquals(HttpStatus.OK, restClient.lastKnownStatusCode)

        Thread.sleep(1000)

        val envelopeCountAfter = testHelper.sentryClient.getEnvelopeCount()
        println(envelopeCountAfter)
        assertTrue(envelopeCountAfter.envelopes!! > envelopeCountBefore.envelopes!!)
    }
}
