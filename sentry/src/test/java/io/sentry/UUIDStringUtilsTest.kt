package io.sentry

import io.sentry.util.UUIDStringUtils
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UUIDStringUtilsTest {
  @Test
  fun `UUID toString matches UUIDStringUtils to String`() {
    val uuid = UUID.randomUUID()
    val sentryIdString = uuid.toString().replace("-", "")
    assertEquals(sentryIdString, UUIDStringUtils.toSentryIdString(uuid))
  }

  @Test
  fun `UUID toString matches UUIDStringUtils to String for SpanId`() {
    val uuid = UUID.randomUUID()
    val sentryIdString = uuid.toString().replace("-", "").substring(0, 16)
    assertEquals(sentryIdString, UUIDStringUtils.toSentrySpanIdString(uuid))
  }
}
