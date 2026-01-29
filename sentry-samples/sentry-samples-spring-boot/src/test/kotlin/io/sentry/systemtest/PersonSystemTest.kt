package io.sentry.systemtest

import io.sentry.protocol.FeatureFlag
import io.sentry.protocol.SentryId
import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class PersonSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `get person fails`() {
    val restClient = testHelper.restClient
    restClient.getPerson(1L)
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureErrorReceived { event ->
      testHelper.doesEventHaveExceptionMessage(event, "Something went wrong [id=1]") &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionHave(
        transaction,
        op = "http.server",
        featureFlag = FeatureFlag("flag.evaluation.my-feature-flag", true),
      )
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

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "PersonService.create") &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "db.query",
          "insert into person (firstName, lastName) values (?, ?)",
        )
    }
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
