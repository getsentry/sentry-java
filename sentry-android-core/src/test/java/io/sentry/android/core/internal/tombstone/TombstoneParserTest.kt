package io.sentry.android.core.internal.tombstone

import io.sentry.ILogger
import io.sentry.JsonObjectWriter
import io.sentry.protocol.DebugMeta
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.mockito.kotlin.mock

class TombstoneParserTest {
  val expectedRegisters =
    setOf(
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
      "x28",
    )

  val inAppIncludes = arrayListOf("io.sentry.samples.android")
  val inAppExcludes = arrayListOf<String>()
  val nativeLibraryDir =
    "/data/app/~~YtXYvdWm5vDHUWYCmVLG_Q==/io.sentry.samples.android-Q2_nG8SyOi4X_6hGGDGE2Q==/lib/arm64"

  @Test
  fun `parses a snapshot tombstone into Event`() {
    val tombstoneStream =
      GZIPInputStream(TombstoneParserTest::class.java.getResourceAsStream("/tombstone.pb.gz"))
    val parser = TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, nativeLibraryDir)
    val event = parser.parse()

    // top-level data
    assertNotNull(event.eventId)
    assertEquals(
      "Fatal signal SIGSEGV (11), SEGV_MAPERR (1), pid = 21891 (io.sentry.samples.android)",
      event.message!!.formatted,
    )
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
    assertEquals("Tombstone", mechanism!!.type)
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

        if (thread.id == crashedThreadId) {
          if (frame.isInApp!!) {
            assert(
              frame.function!!.startsWith(inAppIncludes[0]) ||
                frame.filename!!.startsWith(nativeLibraryDir)
            )
          }
        }
      }

      assert(thread.stacktrace!!.registers!!.keys.containsAll(expectedRegisters))
    }

    // debug-meta
    assertEquals(352, event.debugMeta!!.images!!.size)
    for (image in event.debugMeta!!.images!!) {
      assertEquals("elf", image.type)
      assertNotNull(image.debugId)
      assertNotNull(image.codeId)
      assertNotNull(image.codeFile)
      val imageAddress = image.imageAddr!!.removePrefix("0x").toLong(16)
      assert(imageAddress > 0)
      assert(image.imageSize!! > 0)
    }
  }

  @Test
  fun `coalesces multiple memory mappings into single module`() {
    // Simulate typical Android memory mappings where a single ELF file has multiple
    // mappings with different permissions (r--p, r-xp, r--p, rw-p)
    val buildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      TombstoneProtos.Tombstone.newBuilder()
        .setPid(1234)
        .setTid(1234)
        .setSignalInfo(
          TombstoneProtos.Signal.newBuilder()
            .setNumber(11)
            .setName("SIGSEGV")
            .setCode(1)
            .setCodeName("SEGV_MAPERR")
        )
        // First mapping: r--p at offset 0 (ELF header, has build_id)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libc.so")
            .setBeginAddress(0x7000000000)
            .setEndAddress(0x7000001000)
            .setOffset(0)
            .setRead(true)
            .setWrite(false)
            .setExecute(false)
        )
        // Second mapping: r-xp at offset 0x1000 (executable segment)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libc.so")
            .setBeginAddress(0x7000001000)
            .setEndAddress(0x7000010000)
            .setOffset(0x1000)
            .setRead(true)
            .setWrite(false)
            .setExecute(true)
        )
        // Third mapping: r--p at offset 0x10000 (read-only data)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libc.so")
            .setBeginAddress(0x7000010000)
            .setEndAddress(0x7000011000)
            .setOffset(0x10000)
            .setRead(true)
            .setWrite(false)
            .setExecute(false)
        )
        // Fourth mapping: rw-p at offset 0x11000 (writable data)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libc.so")
            .setBeginAddress(0x7000011000)
            .setEndAddress(0x7000012000)
            .setOffset(0x11000)
            .setRead(true)
            .setWrite(true)
            .setExecute(false)
        )
        .putThreads(
          1234,
          TombstoneProtos.Thread.newBuilder()
            .setId(1234)
            .setName("main")
            .addCurrentBacktrace(
              TombstoneProtos.BacktraceFrame.newBuilder()
                .setPc(0x7000001100)
                .setFunctionName("crash")
                .setFileName("/system/lib64/libc.so")
            )
            .build(),
        )
        .build()

    val parser =
      TombstoneParser(
        ByteArrayInputStream(tombstone.toByteArray()),
        inAppIncludes,
        inAppExcludes,
        nativeLibraryDir,
      )
    val event = parser.parse()

    // All 4 mappings should be coalesced into a single module
    val images = event.debugMeta!!.images!!
    assertEquals(1, images.size)

    val image = images[0]
    assertEquals("/system/lib64/libc.so", image.codeFile)
    assertEquals(buildId, image.codeId)
    // Module should span from first mapping start to last mapping end
    assertEquals("0x7000000000", image.imageAddr)
    assertEquals(0x7000012000 - 0x7000000000, image.imageSize)
  }

  @Test
  fun `handles duplicate mappings at offset 0 on Android`() {
    // On some Android versions, the same ELF can have multiple mappings at offset 0
    // with different permissions (r--p and r-xp both at offset 0)
    val buildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      TombstoneProtos.Tombstone.newBuilder()
        .setPid(1234)
        .setTid(1234)
        .setSignalInfo(
          TombstoneProtos.Signal.newBuilder()
            .setNumber(11)
            .setName("SIGSEGV")
            .setCode(1)
            .setCodeName("SEGV_MAPERR")
        )
        // First mapping: r--p at offset 0
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libdl.so")
            .setBeginAddress(0x7000000000)
            .setEndAddress(0x7000001000)
            .setOffset(0)
            .setRead(true)
            .setWrite(false)
            .setExecute(false)
        )
        // Second mapping: r-xp at offset 0 (duplicate!)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libdl.so")
            .setBeginAddress(0x7000001000)
            .setEndAddress(0x7000002000)
            .setOffset(0)
            .setRead(true)
            .setWrite(false)
            .setExecute(true)
        )
        // Third mapping: r--p at offset 0 (another duplicate!)
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(buildId)
            .setMappingName("/system/lib64/libdl.so")
            .setBeginAddress(0x7000002000)
            .setEndAddress(0x7000003000)
            .setOffset(0)
            .setRead(true)
            .setWrite(false)
            .setExecute(false)
        )
        .putThreads(
          1234,
          TombstoneProtos.Thread.newBuilder()
            .setId(1234)
            .setName("main")
            .addCurrentBacktrace(
              TombstoneProtos.BacktraceFrame.newBuilder()
                .setPc(0x7000001100)
                .setFunctionName("crash")
                .setFileName("/system/lib64/libdl.so")
            )
            .build(),
        )
        .build()

    val parser =
      TombstoneParser(
        ByteArrayInputStream(tombstone.toByteArray()),
        inAppIncludes,
        inAppExcludes,
        nativeLibraryDir,
      )
    val event = parser.parse()

    // All duplicate mappings should be coalesced into a single module
    val images = event.debugMeta!!.images!!
    assertEquals(1, images.size)

    val image = images[0]
    assertEquals("/system/lib64/libdl.so", image.codeFile)
    // Module should span from first to last mapping
    assertEquals("0x7000000000", image.imageAddr)
    assertEquals(0x7000003000 - 0x7000000000, image.imageSize)
  }

  @Test
  fun `debugId falls back to codeId when OleGuidFormatter conversion fails`() {
    // Create a tombstone with a memory mapping that has an invalid buildId
    // (contains 'ZZ' which are not valid hex characters)
    val invalidBuildId = "ZZ00112233445566778899aabbccddeeff00112233"
    val validBuildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      TombstoneProtos.Tombstone.newBuilder()
        .setPid(1234)
        .setTid(1234)
        .setSignalInfo(
          TombstoneProtos.Signal.newBuilder()
            .setNumber(11)
            .setName("SIGSEGV")
            .setCode(1)
            .setCodeName("SEGV_MAPERR")
        )
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(invalidBuildId)
            .setMappingName("/system/lib64/libc.so")
            .setBeginAddress(0x7000000000)
            .setEndAddress(0x7000001000)
            .setOffset(0)
            .setRead(true)
            .setExecute(true)
        )
        .addMemoryMappings(
          TombstoneProtos.MemoryMapping.newBuilder()
            .setBuildId(validBuildId)
            .setMappingName("/system/lib64/libm.so")
            .setBeginAddress(0x7000002000)
            .setEndAddress(0x7000003000)
            .setOffset(0)
            .setRead(true)
            .setExecute(true)
        )
        .putThreads(
          1234,
          TombstoneProtos.Thread.newBuilder()
            .setId(1234)
            .setName("main")
            .addCurrentBacktrace(
              TombstoneProtos.BacktraceFrame.newBuilder()
                .setPc(0x7000000100)
                .setFunctionName("crash")
                .setFileName("/system/lib64/libc.so")
            )
            .build(),
        )
        .build()

    val parser =
      TombstoneParser(
        ByteArrayInputStream(tombstone.toByteArray()),
        inAppIncludes,
        inAppExcludes,
        nativeLibraryDir,
      )
    val event = parser.parse()

    val images = event.debugMeta!!.images!!
    assertEquals(2, images.size)

    // First image has invalid buildId -> debugId should fall back to codeId
    val invalidImage = images.find { it.codeFile == "/system/lib64/libc.so" }!!
    assertEquals(invalidBuildId, invalidImage.codeId)
    assertEquals(invalidBuildId, invalidImage.debugId)

    // Second image has valid buildId -> debugId should be converted
    val validImage = images.find { it.codeFile == "/system/lib64/libm.so" }!!
    assertEquals(validBuildId, validImage.codeId)
    assertEquals("c0bcc3f1-9827-fe65-3058-404b2831d9e6", validImage.debugId)
  }

  @Test
  fun `debug meta images snapshot test`() {
    // also test against a full snapshot so that we can track regressions in the VMA -> module
    // reduction
    val tombstoneStream =
      GZIPInputStream(TombstoneParserTest::class.java.getResourceAsStream("/tombstone.pb.gz"))
    val parser = TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, nativeLibraryDir)
    val event = parser.parse()

    val actualJson = serializeDebugMeta(event.debugMeta!!)
    val expectedJson = readGzippedResourceFile("/tombstone_debug_meta.json.gz")

    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `parses tombstone when nativeLibraryDir is null`() {
    val tombstoneStream =
      GZIPInputStream(TombstoneParserTest::class.java.getResourceAsStream("/tombstone.pb.gz"))
    val parser = TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, null)
    val event = parser.parse()

    // Parsing should succeed without NPE
    assertNotNull(event)
    assertEquals(62, event.threads!!.size)

    // Without nativeLibraryDir, frames can only be marked inApp via inAppIncludes
    // All frames should still have inApp set (either true or false)
    for (thread in event.threads!!) {
      for (frame in thread.stacktrace!!.frames!!) {
        assertNotNull(frame.isInApp)
      }
    }
  }

  private fun serializeDebugMeta(debugMeta: DebugMeta): String {
    val logger = mock<ILogger>()
    val writer = StringWriter()
    val jsonWriter = JsonObjectWriter(writer, 100)
    debugMeta.serialize(jsonWriter, logger)
    return writer.toString()
  }

  private fun readGzippedResourceFile(path: String): String {
    return TombstoneParserTest::class
      .java
      .getResourceAsStream(path)
      ?.let { GZIPInputStream(it) }
      ?.bufferedReader()
      ?.use { it.readText().replace(Regex("[\\n\\r\\s]"), "") }
      ?: throw RuntimeException("Cannot read resource file: $path")
  }
}
