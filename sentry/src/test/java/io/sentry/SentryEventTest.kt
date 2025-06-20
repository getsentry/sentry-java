package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class SentryEventTest {
  @Test
  fun `constructor creates a non empty event id`() =
    assertNotEquals(SentryId.EMPTY_ID, SentryEvent().eventId)

  @Test
  fun `constructor defines timestamp after now`() =
    assertTrue(
      Instant.now()
        .plus(1, ChronoUnit.HOURS)
        .isAfter(Instant.parse(DateUtils.getTimestamp(SentryEvent().timestamp)))
    )

  @Test
  fun `constructor defines timestamp before hour ago`() =
    assertTrue(
      Instant.now()
        .minus(1, ChronoUnit.HOURS)
        .isBefore(Instant.parse(DateUtils.getTimestamp(SentryEvent().timestamp)))
    )

  @Test
  fun `if mechanism is not handled, it should return isCrashed=true`() {
    val mechanism = Mechanism()
    mechanism.isHandled = false
    val event = SentryEvent()
    val factory = SentryExceptionFactory(mock())
    val sentryExceptions =
      factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
    event.exceptions = sentryExceptions
    assertTrue(event.isCrashed)
  }

  @Test
  fun `if mechanism is handled, it should return isCrashed=false`() {
    val mechanism = Mechanism()
    mechanism.isHandled = true
    val event = SentryEvent()
    val factory = SentryExceptionFactory(mock())
    val sentryExceptions =
      factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
    event.exceptions = sentryExceptions
    assertFalse(event.isCrashed)
  }

  @Test
  fun `if mechanism handled flag is null, it should return isCrashed=false`() {
    val mechanism = Mechanism()
    mechanism.isHandled = null
    val event = SentryEvent()
    val factory = SentryExceptionFactory(mock())
    val sentryExceptions =
      factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
    event.exceptions = sentryExceptions
    assertFalse(event.isCrashed)
  }

  @Test
  fun `if mechanism is not set, it should return isCrashed=false`() {
    val event = SentryEvent()
    val factory = SentryExceptionFactory(mock())
    val sentryExceptions = factory.getSentryExceptions(RuntimeException(Throwable()))
    event.exceptions = sentryExceptions
    assertFalse(event.isCrashed)
  }

  @Test
  fun `adds breadcrumb with string as a parameter`() {
    val event = SentryEvent()
    event.addBreadcrumb("breadcrumb")
    assertNotNull(event.breadcrumbs) {
      assertEquals(1, it.filter { it.message == "breadcrumb" }.size)
    }
  }

  @Test
  fun `when throwable is a ExceptionMechanismException, getThrowable unwraps original throwable`() {
    val event = SentryEvent()
    val ex = RuntimeException()
    event.throwable = ExceptionMechanismException(Mechanism(), ex, Thread.currentThread())
    assertEquals(ex, event.getThrowable())
  }

  @Test
  fun `when throwable is not a ExceptionMechanismException, getThrowable returns throwable`() {
    val event = SentryEvent()
    val ex = RuntimeException()
    event.throwable = ex
    assertEquals(ex, event.getThrowable())
  }

  @Test
  fun `when throwable is a ExceptionMechanismException, getThrowableMechanism returns the wrapped throwable`() {
    val event = SentryEvent()
    val ex = RuntimeException()
    val exceptionMechanism = ExceptionMechanismException(Mechanism(), ex, Thread.currentThread())
    event.throwable = exceptionMechanism
    assertEquals(exceptionMechanism, event.throwableMechanism)
  }

  @Test
  fun `when setBreadcrumbs receives immutable list as an argument, its still possible to add more breadcrumbs to the event`() {
    val event =
      SentryEvent().apply {
        breadcrumbs = listOf(Breadcrumb("a"), Breadcrumb("b"))
        addBreadcrumb("c")
      }
    assertNotNull(event.breadcrumbs) {
      assertEquals(listOf("a", "b", "c"), it.map { breadcrumb -> breadcrumb.message })
    }
  }

  @Test
  fun `when setFingerprints receives immutable list as an argument, its still possible to add more fingerprints to the event`() {
    val event =
      SentryEvent().apply {
        fingerprints = listOf("a", "b")
        fingerprints!!.add("c")
      }
    assertNotNull(event.fingerprints) { assertEquals(listOf("a", "b", "c"), it) }
  }

  @Test
  fun `when setExtras receives immutable map as an argument, its still possible to add more extra to the event`() {
    val event =
      SentryEvent().apply {
        extras =
          Collections.unmodifiableMap(mapOf<String, Any>("key1" to "value1", "key2" to "value2"))
        setExtra("key3", "value3")
      }
    assertNotNull(event.extras) {
      assertEquals(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"), it)
    }
  }

  @Test
  fun `when setTags receives immutable map as an argument, its still possible to add more tags to the event`() {
    val event =
      SentryEvent().apply {
        tags = Collections.unmodifiableMap(mapOf("key1" to "value1", "key2" to "value2"))
        setTag("key3", "value3")
      }
    assertNotNull(event.tags) {
      assertEquals(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"), it)
    }
  }

  @Test
  fun `when setModules receives immutable map as an argument, its still possible to add more modules to the event`() {
    val event =
      SentryEvent().apply {
        modules = Collections.unmodifiableMap(mapOf("key1" to "value1", "key2" to "value2"))
        setModule("key3", "value3")
      }
    assertNotNull(event.modules) {
      assertEquals(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"), it)
    }
  }

  @Test
  fun `null tag does not cause NPE`() {
    val event = SentryEvent()

    event.setTag("k", "oldvalue")
    event.setTag(null, null)
    event.setTag("k", null)
    event.setTag(null, "v")

    assertNull(event.getTag(null))
    assertNull(event.getTag("k"))
    assertFalse(event.tags!!.containsKey("k"))
  }

  @Test
  fun `null extra does not cause NPE`() {
    val event = SentryEvent()

    event.setExtra("k", "oldvalue")
    event.setExtra(null, null)
    event.setExtra("k", null)
    event.setExtra(null, "v")

    assertNull(event.getExtra(null))
    assertNull(event.getExtra("k"))
    assertFalse(event.extras!!.containsKey("k"))
  }
}
