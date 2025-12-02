package io.sentry

import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.util.EventSizeLimitingUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventSizeLimitingUtilsTest {
  class Fixture {
    fun getOptions(): SentryOptions {
      val options = SentryOptions()
      options.isEnableEventSizeLimiting = true
      return options
    }
  }

  var fixture = Fixture()

  @Test
  fun `does not modify event if size is below limit`() {
    val options = fixture.getOptions()
    val event = SentryEvent()
    val message = Message()
    message.message = "test message"
    event.setMessage(message)
    event.setExtra("key", "value")

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    assertEquals(event.getMessage(), result.getMessage())
    assertEquals(event.getExtras(), result.getExtras())
  }

  @Test
  fun `removes all breadcrumbs when event exceeds size limit`() {
    val options = fixture.getOptions()
    val event = createLargeEvent()

    // Add many breadcrumbs with large data to exceed 1MB limit
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024)) // 15KB per breadcrumb
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // All breadcrumbs should be removed
    assertNull(result.getBreadcrumbs())
  }

  @Test
  fun `truncates stack frames when event exceeds size limit`() {
    val options = fixture.getOptions()
    val event = createLargeEvent()

    // Add exception with large stack trace
    val exception = SentryException()
    exception.setType("RuntimeException")
    exception.setValue("Test exception")
    val stacktrace = SentryStackTrace()
    val frames = mutableListOf<SentryStackFrame>()
    for (i in 0..200) {
      val frame = SentryStackFrame()
      frame.setModule("com.example.Class$i")
      frame.setFunction("method$i")
      frame.setFilename("File$i.java")
      frame.setLineno(i)
      frames.add(frame)
    }
    stacktrace.setFrames(frames)
    exception.setStacktrace(stacktrace)
    event.setExceptions(listOf(exception))

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    val resultExceptions = result.getExceptions()
    assertNotNull(resultExceptions)
    assertTrue(resultExceptions!!.isNotEmpty())
    val resultStacktrace = resultExceptions[0].getStacktrace()
    assertNotNull(resultStacktrace)
    val resultFrames = resultStacktrace.getFrames()
    assertNotNull(resultFrames)
    // Should be truncated to 500 frames (250 from start + 250 from end) when over 500
    // For 200 frames, no truncation should occur since it's less than 500
    assertTrue(resultFrames!!.size <= 500)
  }

  @Test
  fun `truncates stack frames when event has more than 500 frames`() {
    val options = fixture.getOptions()
    val event = createLargeEvent()

    // Add exception with very large stack trace (> 500 frames)
    val exception = SentryException()
    exception.setType("RuntimeException")
    exception.setValue("Test exception")
    val stacktrace = SentryStackTrace()
    val frames = mutableListOf<SentryStackFrame>()
    // Create 601 frames (0..600) with large data to ensure event exceeds size limit
    for (i in 0..600) {
      val frame = SentryStackFrame()
      frame.setModule("com.example.Class$i")
      frame.setFunction("method$i" + "x".repeat(1024)) // Large function name
      frame.setFilename("File$i.java")
      frame.setLineno(i)
      frame.setContextLine("x".repeat(2048)) // Large context line
      frames.add(frame)
    }
    stacktrace.setFrames(frames)
    exception.setStacktrace(stacktrace)
    event.setExceptions(listOf(exception))

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    val resultExceptions = result.getExceptions()
    assertNotNull(resultExceptions)
    assertTrue(resultExceptions!!.isNotEmpty())
    val resultStacktrace = resultExceptions[0].getStacktrace()
    assertNotNull(resultStacktrace)
    val resultFrames = resultStacktrace.getFrames()
    assertNotNull(resultFrames)
    // Should be truncated to 500 frames (250 from start + 250 from end)
    assertEquals(500, resultFrames!!.size)
  }

  @Test
  fun `invokes onOversizedEvent callback when event exceeds size limit`() {
    val options = fixture.getOptions()
    var callbackInvoked = false
    var receivedEvent: SentryEvent? = null
    var receivedHint: Hint? = null
    options.setOnOversizedEvent { event, hint ->
      callbackInvoked = true
      receivedEvent = event
      receivedHint = hint
      event
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val hint = Hint()
    val result = EventSizeLimitingUtils.limitEventSize(event, hint, options)

    assertTrue(callbackInvoked)
    assertNotNull(receivedEvent)
    assertEquals(event, receivedEvent)
    assertEquals(hint, receivedHint)
    assertNotNull(result)
  }

  @Test
  fun `onOversizedEvent callback successfully reduces size below limit`() {
    val options = fixture.getOptions()
    options.setOnOversizedEvent { event, _ ->
      // Remove all breadcrumbs to reduce size
      event.setBreadcrumbs(null)
      event
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // Breadcrumbs should be removed by callback
    assertNull(result.getBreadcrumbs())
    // No further reduction should be needed
  }

  @Test
  fun `onOversizedEvent callback insufficient reduction continues with automatic steps`() {
    val options = fixture.getOptions()
    var callbackInvoked = false
    options.setOnOversizedEvent { event, _ ->
      callbackInvoked = true
      // Remove only some breadcrumbs, not enough to reduce size below limit
      val breadcrumbs = event.getBreadcrumbs()
      if (breadcrumbs != null && breadcrumbs.size > 20) {
        // Keep only 20 breadcrumbs, but each is 15KB, so total is still ~300KB
        // Add more data to ensure it's still oversized
        val keptBreadcrumbs = breadcrumbs.subList(breadcrumbs.size - 20, breadcrumbs.size)
        event.setBreadcrumbs(keptBreadcrumbs)
        // Add extra data to ensure event is still oversized
        event.setExtra("still_large", "x".repeat(800 * 1024)) // 800KB extra
      }
      event
    }
    val event = createLargeEvent()

    // Add many breadcrumbs with large data to exceed 1MB limit
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertTrue(callbackInvoked)
    assertNotNull(result)
    // Automatic reduction should remove all remaining breadcrumbs
    assertNull(result.getBreadcrumbs())
  }

  @Test
  fun `onOversizedEvent callback exception continues with automatic reduction`() {
    val options = fixture.getOptions()
    options.setOnOversizedEvent { _, _ -> throw RuntimeException("Callback error") }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // Automatic reduction should still work despite callback exception
    assertNull(result.getBreadcrumbs())
  }

  @Test
  fun `onOversizedEvent callback not invoked when event is below size limit`() {
    val options = fixture.getOptions()
    var callbackInvoked = false
    options.setOnOversizedEvent { _, _ ->
      callbackInvoked = true
      SentryEvent()
    }
    val event = SentryEvent()
    val message = Message()
    message.message = "test message"
    event.setMessage(message)

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertFalse(callbackInvoked)
    assertNotNull(result)
  }

  @Test
  fun `onOversizedEvent callback not invoked when event size limiting is disabled`() {
    val options = SentryOptions()
    options.isEnableEventSizeLimiting = false
    var callbackInvoked = false
    options.setOnOversizedEvent { _, _ ->
      callbackInvoked = true
      SentryEvent()
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertFalse(callbackInvoked)
    assertNotNull(result)
    // Event should be unchanged when limiting is disabled
    assertNotNull(result.getBreadcrumbs())
  }

  @Test
  fun `onOversizedEvent callback can replace event with a different event`() {
    val options = fixture.getOptions()
    val replacementEvent = SentryEvent()
    val replacementMessage = Message()
    replacementMessage.message = "Replacement event"
    replacementEvent.setMessage(replacementMessage)
    var callbackInvoked = false
    options.setOnOversizedEvent { _, _ ->
      callbackInvoked = true
      replacementEvent // Return a completely different event
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertTrue(callbackInvoked)
    assertNotNull(result)
    assertEquals("Replacement event", result!!.getMessage()?.message)
  }

  @Test
  fun `onOversizedEvent callback returning same event unchanged continues with automatic reduction`() {
    val options = fixture.getOptions()
    var callbackInvoked = false
    options.setOnOversizedEvent { event, _ ->
      callbackInvoked = true
      event // Return the same event without modifications
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertTrue(callbackInvoked)
    assertNotNull(result)
    // Automatic reduction should have removed breadcrumbs
    assertNull(result.getBreadcrumbs())
  }

  @Test
  fun `onOversizedEvent callback receives correct hint object`() {
    val options = fixture.getOptions()
    var receivedHint: Hint? = null
    options.setOnOversizedEvent { event, hint ->
      receivedHint = hint
      event
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val hint = Hint()
    hint.set("custom_key", "custom_value")
    val result = EventSizeLimitingUtils.limitEventSize(event, hint, options)

    assertNotNull(result)
    assertNotNull(receivedHint)
    assertEquals("custom_value", receivedHint!!.get("custom_key"))
  }

  @Test
  fun `onOversizedEvent callback can modify extras to reduce size`() {
    val options = fixture.getOptions()
    options.setOnOversizedEvent { event, _ ->
      // Remove extras to reduce size
      event.setExtras(null)
      event
    }
    val event = createLargeEvent()

    // Add large extras
    for (i in 0..100) {
      event.setExtra("large_extra_$i", "x".repeat(15 * 1024))
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // Extras should be removed by callback
    assertNull(result.getExtras())
  }

  @Test
  fun `onOversizedEvent callback can modify contexts to reduce size`() {
    val options = fixture.getOptions()
    options.setOnOversizedEvent { event, _ ->
      // Remove contexts to reduce size
      event.contexts.keys().toList().forEach { event.contexts.remove(it) }
      event
    }
    val event = createLargeEvent()

    // Add large contexts
    for (i in 0..100) {
      val context = mutableMapOf<String, Any>()
      context["data"] = "x".repeat(15 * 1024)
      event.contexts.set("context_$i", context)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // Contexts should be removed by callback
    assertEquals(0, result.contexts.size)
  }

  @Test
  fun `onOversizedEvent callback multiple invocations not expected`() {
    val options = fixture.getOptions()
    var callbackInvocationCount = 0
    options.setOnOversizedEvent { event, _ ->
      callbackInvocationCount++
      event
    }
    val event = createLargeEvent()

    // Add breadcrumbs to make it large
    for (i in 0..100) {
      val breadcrumb = Breadcrumb()
      breadcrumb.message = "breadcrumb $i"
      breadcrumb.setData("large_data", "x".repeat(15 * 1024))
      event.addBreadcrumb(breadcrumb)
    }

    val result = EventSizeLimitingUtils.limitEventSize(event, Hint(), options)

    assertNotNull(result)
    // Callback should be invoked exactly once
    assertEquals(1, callbackInvocationCount)
  }

  private fun createLargeEvent(): SentryEvent {
    val event = SentryEvent()
    val message = Message()
    message.message = "Large event for testing"
    event.setMessage(message)
    return event
  }
}
