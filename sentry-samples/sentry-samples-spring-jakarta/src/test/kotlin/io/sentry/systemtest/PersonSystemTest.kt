package io.sentry.systemtest

import io.sentry.protocol.SentryId
import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class PersonSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080/sentry-samples-spring-jakarta-0.0.1-SNAPSHOT")
    testHelper.reset()
  }

  @Test
  fun `get person fails`() {
    val restClient = testHelper.restClient
    restClient.getPerson(11L)
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureErrorReceived { event ->
      testHelper.doesEventHaveExceptionMessage(event, "Something went wrong [id=11]") &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionHaveOp(transaction, "http.server")
    }

    Thread.sleep(10000)

    testHelper.ensureLogsReceived { logs, envelopeHeader ->
      testHelper.doesContainLogWithBody(logs, "warn Sentry logging") &&
        testHelper.doesContainLogWithBody(logs, "error Sentry logging") &&
        testHelper.doesContainLogWithBody(logs, "hello there world!")
    }
  }

  @Test
  fun `create person works`() {
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson = restClient.createPerson(person)
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)
  }

  @Test
  fun `create person starts a profile linked to the transaction`() {
    var profilerId: SentryId? = null
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson = restClient.createPerson(person)
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      profilerId = transaction.contexts.profile?.profilerId
      transaction.transaction == "POST /person/"
    }
    testHelper.ensureProfileChunkReceived { profileChunk, envelopeHeader ->
      profileChunk.profilerId == profilerId
    }
  }
}
