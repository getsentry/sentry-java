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
  fun `when debug meta already has proguard image, does not attach options proguard uuid`() {
    val options = SentryOptions().apply { proguardUuid = "current-id" }
    val debugMeta =
      DebugMeta.buildDebugMeta(
        DebugMeta().apply {
          images =
            listOf(
              DebugImage().apply {
                type = DebugImage.PROGUARD
                uuid = "existing-id"
              }
            )
        },
        options,
      )

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals(1, images.size)
        assertEquals("existing-id", images[0].uuid)
        assertEquals(DebugImage.PROGUARD, images[0].type)
      }
    }
  }

  @Test
  fun `when debug meta already has proguard image, still attaches missing bundle ids`() {
    val options =
      SentryOptions().apply {
        proguardUuid = "current-id"
        bundleIds.add("bundle-id")
      }
    val debugMeta =
      DebugMeta.buildDebugMeta(
        DebugMeta().apply {
          images =
            listOf(
              DebugImage().apply {
                type = DebugImage.PROGUARD
                uuid = "existing-id"
              }
            )
        },
        options,
      )

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals(2, images.size)
        assertEquals(DebugImage.PROGUARD, images[0].type)
        assertEquals("existing-id", images[0].uuid)
        assertEquals(DebugImage.JVM, images[1].type)
        assertEquals("bundle-id", images[1].debugId)
      }
    }
  }

  @Test
  fun `when debug meta has unrelated debug image, attaches option debug information`() {
    val options =
      SentryOptions().apply {
        proguardUuid = "proguard-id"
        bundleIds.add("bundle-id")
      }
    val debugMeta =
      DebugMeta.buildDebugMeta(
        DebugMeta().apply {
          images =
            listOf(
              DebugImage().apply {
                type = "elf"
                debugId = "native-id"
              }
            )
        },
        options,
      )

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals(3, images.size)
        assertEquals("elf", images[0].type)
        assertEquals("native-id", images[0].debugId)
        assertEquals(DebugImage.PROGUARD, images[1].type)
        assertEquals("proguard-id", images[1].uuid)
        assertEquals(DebugImage.JVM, images[2].type)
        assertEquals("bundle-id", images[2].debugId)
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

  @Test
  fun `when debug meta already has jvm image, only attaches missing bundle ids`() {
    val options = SentryOptions().apply { bundleIds.addAll(listOf("id1", "id2")) }
    val debugMeta =
      DebugMeta.buildDebugMeta(
        DebugMeta().apply {
          images =
            listOf(
              DebugImage().apply {
                type = DebugImage.JVM
                debugId = "id1"
              }
            )
        },
        options,
      )

    assertNotNull(debugMeta) {
      assertNotNull(it.images) { images ->
        assertEquals(2, images.size)
        assertEquals("id1", images[0].debugId)
        assertEquals(DebugImage.JVM, images[0].type)
        assertEquals("id2", images[1].debugId)
        assertEquals(DebugImage.JVM, images[1].type)
      }
    }
  }
}
