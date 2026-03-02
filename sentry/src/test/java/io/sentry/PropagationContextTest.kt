package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

  // Decision matrix tests for shouldContinueTrace

  private val incomingTraceId = "bc6d53f15eb88f4320054569b8c553d4"
  private val sentryTrace = "bc6d53f15eb88f4320054569b8c553d4-b72fa28504b07285-1"

  private fun makeOptions(dsnOrgId: String?, explicitOrgId: String? = null, strict: Boolean = false): SentryOptions {
    val options = SentryOptions()
    if (dsnOrgId != null) {
      options.dsn = "https://key@o$dsnOrgId.ingest.sentry.io/123"
    } else {
      options.dsn = "https://key@sentry.io/123"
    }
    options.orgId = explicitOrgId
    options.isStrictTraceContinuation = strict
    return options
  }

  private fun makeBaggage(orgId: String?): String {
    val parts = mutableListOf("sentry-trace_id=$incomingTraceId")
    if (orgId != null) {
      parts.add("sentry-org_id=$orgId")
    }
    return parts.joinToString(",")
  }

  @Test
  fun `strict=false, matching orgs - continues trace`() {
    val options = makeOptions(dsnOrgId = "1", strict = false)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=false, baggage missing org - continues trace`() {
    val options = makeOptions(dsnOrgId = "1", strict = false)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage(null), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=false, sdk missing org - continues trace`() {
    val options = makeOptions(dsnOrgId = null, strict = false)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=false, both missing org - continues trace`() {
    val options = makeOptions(dsnOrgId = null, strict = false)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage(null), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=false, mismatched orgs - starts new trace`() {
    val options = makeOptions(dsnOrgId = "2", strict = false)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertNotEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=true, matching orgs - continues trace`() {
    val options = makeOptions(dsnOrgId = "1", strict = true)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=true, baggage missing org - starts new trace`() {
    val options = makeOptions(dsnOrgId = "1", strict = true)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage(null), options)
    assertNotEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=true, sdk missing org - starts new trace`() {
    val options = makeOptions(dsnOrgId = null, strict = true)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertNotEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=true, both missing org - continues trace`() {
    val options = makeOptions(dsnOrgId = null, strict = true)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage(null), options)
    assertEquals(incomingTraceId, pc.traceId.toString())
  }

  @Test
  fun `strict=true, mismatched orgs - starts new trace`() {
    val options = makeOptions(dsnOrgId = "2", strict = true)
    val pc = PropagationContext.fromHeaders(NoOpLogger.getInstance(), sentryTrace, makeBaggage("1"), options)
    assertNotEquals(incomingTraceId, pc.traceId.toString())
  }
}
