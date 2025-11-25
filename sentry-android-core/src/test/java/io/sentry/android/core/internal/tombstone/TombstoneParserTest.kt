package io.sentry.android.core.internal.tombstone

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TombstoneParserTest {
  val expectedRegisters = setOf(
    "x8",
    "x9",
    "esr",
    "lr",
    "pst",
    "x10",
    "x12",
    "x11",
    "x14",
    "x13",
    "x16",
    "x15",
    "sp",
    "x18",
    "x17",
    "x19",
    "pc",
    "x21",
    "x20",
    "x0",
    "x23",
    "x1",
    "x22",
    "x2",
    "x25",
    "x3",
    "x24",
    "x4",
    "x27",
    "x5",
    "x26",
    "x6",
    "x29",
    "x7",
    "x28"
  )

  @Test
  fun `parses a snapshot tombstone into Event`() {
    val tombstone = File("src/test/resources/tombstone.pb")
    val parser = TombstoneParser(tombstone.inputStream())
    val event = parser.parse()

    // top-level data
    assertNotNull(event.eventId)
    assertEquals("Fatal signal SIGSEGV (11), SEGV_MAPERR (1), pid = 21891 (io.sentry.samples.android)", event.message!!.formatted)
    assertEquals("native", event.platform)
    assertEquals("FATAL", event.level!!.name)

    // exception
    // we only track one native exception (no nesting, one crashed thread)
    assertEquals(1, event.exceptions!!.size)
    val exception = event.exceptions!![0]
    assertEquals("SIGSEGV", exception.type)
    assertEquals("Segfault", exception.value)
    val crashedThreadId = exception.threadId
    assertNotNull(crashedThreadId)

    val mechanism = exception.mechanism
    assertEquals("signalhandler", mechanism!!.type)
    assertEquals(false, mechanism.isHandled)
    assertEquals(true, mechanism.synthetic)
    assertEquals("SIGSEGV", mechanism.meta!!["name"])
    assertEquals(11, mechanism.meta!!["number"])
    assertEquals("SEGV_MAPERR", mechanism.meta!!["code_name"])
    assertEquals(1, mechanism.meta!!["code"])

    // threads
    assertEquals(62, event.threads!!.size)
    for (thread in event.threads!!) {
      assertNotNull(thread.id)
      if (thread.id == crashedThreadId) {
        assert(thread.isCrashed == true)
      }
      assert(thread.stacktrace!!.frames!!.isNotEmpty())

      for (frame in thread.stacktrace!!.frames!!) {
        assertNotNull(frame.function)
        assertNotNull(frame.`package`)
        assertNotNull(frame.instructionAddr)
      }

      assert(thread.stacktrace!!.registers!!.keys.containsAll(expectedRegisters))
    }

    // debug-meta
    assertEquals(357, event.debugMeta!!.images!!.size)
    for (image in event.debugMeta!!.images!!) {
      assertEquals("elf", image.type)
      assertNotNull(image.debugId)
      assertNotNull(image.codeId)
      assertEquals(image.codeId, image.debugId)
      assertNotNull(image.codeFile)
      val imageAddress = image.imageAddr!!.removePrefix("0x").toLong(16)
      assert(imageAddress > 0)
      assert(image.imageSize!! > 0)
    }
  }
}
