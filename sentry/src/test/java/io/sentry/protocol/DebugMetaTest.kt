package io.sentry.protocol

import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DebugMetaTest {
  @Test
  fun `when setDebugImages receives immutable list as an argument, its still possible to add more debugImages`() {
    val meta =
      DebugMeta().apply {
        images = listOf(DebugImage(), DebugImage())
        images!! += DebugImage()
      }
    assertNotNull(meta.images) { assertEquals(3, it.size) }
  }

  @Test
  fun `when event does not have debug meta and proguard uuids are set, attaches debug information`() {
    val options = SentryOptions().apply { proguardUuid = "id1" }
    val debugMeta = DebugMeta.buildDebugMeta(null, options)

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals("id1", images[0].uuid)
        assertEquals("proguard", images[0].type)
      }
    }
  }

  @Test
  fun `when event does not have debug meta and bundle ids are set, attaches debug information`() {
    val options = SentryOptions().apply { bundleIds.addAll(listOf("id1", "id2")) }
    val debugMeta = DebugMeta.buildDebugMeta(null, options)

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals("id1", images[0].debugId)
        assertEquals("jvm", images[0].type)
        assertEquals("id2", images[1].debugId)
        assertEquals("jvm", images[1].type)
      }
    }
  }

  @Test
  fun `when event has debug meta and proguard uuids are set, attaches debug information`() {
    val options = SentryOptions().apply { proguardUuid = "id1" }
    val debugMeta = DebugMeta.buildDebugMeta(DebugMeta(), options)

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals("id1", images[0].uuid)
        assertEquals("proguard", images[0].type)
      }
    }
  }

  @Test
  fun `when event has debug meta and bundle ids are set, attaches debug information`() {
    val options = SentryOptions().apply { bundleIds.addAll(listOf("id1", "id2")) }
    val debugMeta = DebugMeta.buildDebugMeta(DebugMeta(), options)

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals("id1", images[0].debugId)
        assertEquals("jvm", images[0].type)
        assertEquals("id2", images[1].debugId)
        assertEquals("jvm", images[1].type)
      }
    }
  }

  @Test
  fun `when event has debug meta as well as images and bundle ids are set, attaches debug information`() {
    val options = SentryOptions().apply { bundleIds.addAll(listOf("id1", "id2")) }
    val debugMeta = DebugMeta.buildDebugMeta(DebugMeta().also { it.images = listOf() }, options)

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals("id1", images[0].debugId)
        assertEquals("jvm", images[0].type)
        assertEquals("id2", images[1].debugId)
        assertEquals("jvm", images[1].type)
      }
    }
  }
}
