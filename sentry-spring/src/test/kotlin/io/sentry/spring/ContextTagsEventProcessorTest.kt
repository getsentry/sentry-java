package io.sentry.spring

import io.sentry.SentryEvent
import io.sentry.SentryOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.slf4j.MDC

class ContextTagsEventProcessorTest {
  class Fixture {
    fun getSut(
      contextTags: List<String> = emptyList(),
      mdcTags: Map<String, String> = emptyMap(),
    ): ContextTagsEventProcessor {
      val options = SentryOptions().apply { contextTags.forEach { tag -> addContextTag(tag) } }
      val sut = ContextTagsEventProcessor(options)
      mdcTags.forEach { MDC.put(it.key, it.value) }
      return sut
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun before() {
    MDC.clear()
  }

  @Test
  fun `does not copy tags if no tags are set on options`() {
    val sut = fixture.getSut()

    val result = sut.process(SentryEvent(), null)
    val tags = result.tags
    assertTrue(tags == null || tags.isEmpty())
  }

  @Test
  fun `copies mdc tags`() {
    val sut =
      fixture.getSut(contextTags = listOf("user-id"), mdcTags = mapOf("user-id" to "user-id-value"))

    val result = sut.process(SentryEvent(), null)
    val tags = result.tags
    assertNotNull(tags) {
      assertTrue(it.containsKey("user-id"))
      assertEquals("user-id-value", it["user-id"])
    }
  }

  @Test
  fun `does not copy tags not defined in options`() {
    val sut =
      fixture.getSut(
        contextTags = listOf("user-id"),
        mdcTags = mapOf("user-id" to "user-id-value", "request-id" to "request-id-value"),
      )

    val result = sut.process(SentryEvent(), null)
    val tags = result.tags
    assertNotNull(tags) {
      assertTrue(it.containsKey("user-id"))
      assertFalse(it.containsKey("request-id"))
    }
  }

  @Test
  fun `does not copy tag not set in MDC`() {
    val sut =
      fixture.getSut(
        contextTags = listOf("user-id", "another-tag"),
        mdcTags = mapOf("user-id" to "user-id-value"),
      )

    val result = sut.process(SentryEvent(), null)
    val tags = result.tags
    assertNotNull(tags) {
      assertTrue(it.containsKey("user-id"))
      assertFalse(it.containsKey("another-tag"))
    }
  }
}
