package io.sentry.android.core.internal.tombstone

import com.abovevacant.epitaph.core.BacktraceFrame
import com.abovevacant.epitaph.core.MemoryMapping
import com.abovevacant.epitaph.core.Signal
import com.abovevacant.epitaph.core.Tombstone
import com.abovevacant.epitaph.core.TombstoneThread
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.JsonObjectWriter
import io.sentry.protocol.DebugMeta
import java.io.StringWriter
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    "/data/app/~~gu-2hA9_Zg6tfIuDAbLpKA==/io.sentry.samples.android-MFqmKAMnl9AjNlHcO3mejA==/lib/arm64"

  val parser = TombstoneParser(inAppIncludes, inAppExcludes, nativeLibraryDir)

  @Test
  fun `parses a snapshot tombstone into Event`() {
    val tombstoneStream =
      GZIPInputStream(TombstoneParserTest::class.java.getResourceAsStream("/tombstone.pb.gz"))
    val streamParser =
      TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, nativeLibraryDir)
    val event = streamParser.parse()

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
    val mainThread = event.threads!!.single { it.isMain == true }
    assertEquals(21891, mainThread.id)
    assertEquals("main", mainThread.name)

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
                frame.`package`!!.startsWith(nativeLibraryDir)
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
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        // First mapping: r--p at offset 0 (ELF header, has build_id)
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0,
            true,
            false,
            false,
            "/system/lib64/libc.so",
            buildId,
            0,
          )
        )
        // Second mapping: r-xp at offset 0x1000 (executable segment)
        .addMemoryMapping(
          MemoryMapping(
            0x7000001000,
            0x7000010000,
            0x1000,
            true,
            false,
            true,
            "/system/lib64/libc.so",
            buildId,
            0,
          )
        )
        // Third mapping: r--p at offset 0x10000 (read-only data)
        .addMemoryMapping(
          MemoryMapping(
            0x7000010000,
            0x7000011000,
            0x10000,
            true,
            false,
            false,
            "/system/lib64/libc.so",
            buildId,
            0,
          )
        )
        // Fourth mapping: rw-p at offset 0x11000 (writable data)
        .addMemoryMapping(
          MemoryMapping(
            0x7000011000,
            0x7000012000,
            0x11000,
            true,
            true,
            false,
            "/system/lib64/libc.so",
            buildId,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            1234,
            "main",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x7000001100, 0, "crash", 0, "/system/lib64/libc.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .build()

    val event = parser.parse(tombstone)

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
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        // First mapping: r--p at offset 0
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0,
            true,
            false,
            false,
            "/system/lib64/libdl.so",
            buildId,
            0,
          )
        )
        // Second mapping: r-xp at offset 0 (duplicate!)
        .addMemoryMapping(
          MemoryMapping(
            0x7000001000,
            0x7000002000,
            0,
            true,
            false,
            true,
            "/system/lib64/libdl.so",
            buildId,
            0,
          )
        )
        // Third mapping: r--p at offset 0 (another duplicate!)
        .addMemoryMapping(
          MemoryMapping(
            0x7000002000,
            0x7000003000,
            0,
            true,
            false,
            false,
            "/system/lib64/libdl.so",
            buildId,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            1234,
            "main",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x7000001100, 0, "crash", 0, "/system/lib64/libdl.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .build()

    val event = parser.parse(tombstone)

    val images = event.debugMeta!!.images!!
    assertEquals(1, images.size)

    val image = images[0]
    assertEquals("/system/lib64/libdl.so", image.codeFile)
    // Module should span from first to last mapping
    assertEquals("0x7000000000", image.imageAddr)
    assertEquals(0x7000003000 - 0x7000000000, image.imageSize)
  }

  @Test
  fun `creates images for multiple ELF files embedded in same APK`() {
    val apkPath = "/data/app/io.sentry.sample/base.apk"
    val firstBuildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"
    val secondBuildId = "a1647a1813da20ea7e0dad6cbc11486dfaeaeb8e"

    val tombstone =
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0x156c000,
            true,
            false,
            true,
            apkPath,
            firstBuildId,
            0,
          )
        )
        .addMemoryMapping(
          MemoryMapping(
            0x7000001000,
            0x7000002000,
            0x156d000,
            true,
            false,
            false,
            apkPath,
            firstBuildId,
            0,
          )
        )
        .addMemoryMapping(
          MemoryMapping(
            0x7100000000,
            0x7100001000,
            0x1578000,
            true,
            false,
            true,
            apkPath,
            secondBuildId,
            0,
          )
        )
        .addMemoryMapping(
          MemoryMapping(
            0x7100001000,
            0x7100003000,
            0x1579000,
            true,
            false,
            false,
            apkPath,
            secondBuildId,
            0,
          )
        )
        .build()

    val images = parser.parse(tombstone).debugMeta!!.images!!

    assertThat(images).hasSize(2)
    assertThat(images.map { it.codeFile }).containsExactly(apkPath, apkPath)
    assertThat(images.map { it.codeId }).containsExactly(firstBuildId, secondBuildId).inOrder()
    assertThat(images.map { it.imageAddr })
      .containsExactly("0x7000000000", "0x7100000000")
      .inOrder()
    assertThat(images.map { it.imageSize }).containsExactly(0x2000L, 0x3000L).inOrder()
  }

  @Test
  fun `coalesces multiple ELF continuations aligned for 16 KiB pages`() {
    val apkPath = "/data/app/io.sentry.sample/base.apk"
    val buildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        .pageSize(0x4000)
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0x156c000,
            true,
            false,
            true,
            apkPath,
            buildId,
            0,
          )
        )
        // The virtual-address progression can differ from the file-offset progression by one page.
        .addMemoryMapping(
          MemoryMapping(
            0x7000005000,
            0x7000007000,
            0x156d000,
            true,
            false,
            false,
            apkPath,
            "",
            0,
          )
        )
        // Each PT_LOAD adds one page of alignment drift. Validation must compare adjacent
        // mappings so this drift does not accumulate from the start of the module.
        .addMemoryMapping(
          MemoryMapping(
            0x700000b000,
            0x700000d000,
            0x156f000,
            true,
            true,
            false,
            apkPath,
            "",
            0,
          )
        )
        .build()

    val image = parser.parse(tombstone).debugMeta!!.images!!.single()

    assertThat(image.imageAddr).isEqualTo("0x7000000000")
    assertThat(image.imageSize).isEqualTo(0xd000)
  }

  @Test
  fun `does not include a different embedded ELF without build ID in previous image`() {
    val apkPath = "/data/app/io.sentry.sample/base.apk"
    val buildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        .pageSize(0x4000)
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0x156c000,
            true,
            false,
            true,
            apkPath,
            buildId,
            0,
          )
        )
        // A different ELF in the APK that does not have a GNU build ID.
        .addMemoryMapping(
          MemoryMapping(
            0x7100000000,
            0x7100010000,
            0x163c000,
            true,
            false,
            true,
            apkPath,
            "",
            0x4000,
          )
        )
        .build()

    val image = parser.parse(tombstone).debugMeta!!.images!!.single()

    assertThat(image.codeId).isEqualTo(buildId)
    assertThat(image.imageAddr).isEqualTo("0x7000000000")
    assertThat(image.imageSize).isEqualTo(0x1000)
  }

  @Test
  fun `sets image address on frame for ELF embedded in APK`() {
    val apkPath = "/data/app/io.sentry.sample/base.apk"
    val buildId = "a1647a1813da20ea7e0dad6cbc11486dfaeaeb8e"
    val imageAddress = 0x7000000000
    val relativePc = 0xac4L

    val tombstone =
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        .addMemoryMapping(
          MemoryMapping(
            imageAddress,
            imageAddress + 0x1000,
            0x156c000,
            true,
            false,
            true,
            apkPath,
            buildId,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            1234,
            "main",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
              BacktraceFrame(
                relativePc,
                imageAddress + relativePc,
                0,
                "crash",
                0,
                "$apkPath!libnative-sample.so",
                0x156c000,
                buildId,
              )
            ),
            emptyList(),
            0,
            0,
          )
        )
        .build()

    val frame = parser.parse(tombstone).threads!!.single().stacktrace!!.frames!!.single()

    assertThat(frame.instructionAddr).isEqualTo("0x7000000ac4")
    assertThat(frame.imageAddr).isEqualTo("0x7000000000")
  }

  @Test
  fun `debugId falls back to codeId when OleGuidFormatter conversion fails`() {
    // Create a tombstone with a memory mapping that has an invalid buildId
    // (contains 'ZZ' which are not valid hex characters)
    val invalidBuildId = "ZZ00112233445566778899aabbccddeeff00112233"
    val validBuildId = "f1c3bcc0279865fe3058404b2831d9e64135386c"

    val tombstone =
      Tombstone.Builder()
        .pid(1234)
        .tid(1234)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        .addMemoryMapping(
          MemoryMapping(
            0x7000000000,
            0x7000001000,
            0,
            true,
            false,
            true,
            "/system/lib64/libc.so",
            invalidBuildId,
            0,
          )
        )
        .addMemoryMapping(
          MemoryMapping(
            0x7000002000,
            0x7000003000,
            0,
            true,
            false,
            true,
            "/system/lib64/libm.so",
            validBuildId,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            1234,
            "main",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x7000000100, 0, "crash", 0, "/system/lib64/libc.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .build()

    val event = parser.parse(tombstone)

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
  fun `parses APK embedded ELF from full tombstone fixture`() {
    val tombstoneStream =
      GZIPInputStream(
        TombstoneParserTest::class.java.getResourceAsStream("/tombstone_apk_embedded.pb.gz")
      )
    val event =
      TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, nativeLibraryDir).parse()
    val buildId = "a1647a1813da20ea7e0dad6cbc11486dfaeaeb8e"

    val images = event.debugMeta!!.images!!
    val image = images.single { it.codeId == buildId }

    assertThat(image.type).isEqualTo("elf")
    assertThat(image.codeFile).endsWith("/base.apk")
    assertThat(image.debugId).isEqualTo("187a64a1-da13-ea20-7e0d-ad6cbc11486d")
    assertThat(image.imageAddr).isEqualTo("0x7646619000")
    assertThat(image.imageSize).isEqualTo(0x5000)

    val frames =
      event.threads!!
        .flatMap { it.stacktrace!!.frames!! }
        .filter { it.`package`!!.endsWith("base.apk!libnative-sample.so") }

    assertThat(frames).hasSize(2)
    assertThat(frames.map { it.imageAddr }).containsExactly("0x7646619000", "0x7646619000")
    assertThat(frames.map { it.instructionAddr }).containsExactly("0x7646619ac4", "0x7646619ae4")

    // This tombstone was captured on a 4 KiB device, but these ELFs use 16 KiB PT_LOAD
    // alignment. Their mappings still need to be fully coalesced.
    assertThat(images.single { it.codeId == "f86d542eccd3f652ab08ff210b5d009ef6c14cfe" }.imageSize)
      .isEqualTo(0xcc000)
    assertThat(images.single { it.codeId == "d76a948eaadeea8d0d1b17cdb026d8b4e4c39384" }.imageSize)
      .isEqualTo(0x9000)
  }

  @Test
  fun `debug meta images snapshot test`() {
    // test against a full snapshot so that we can track regressions in the VMA -> module reduction
    val tombstoneStream =
      GZIPInputStream(TombstoneParserTest::class.java.getResourceAsStream("/tombstone.pb.gz"))
    val streamParser =
      TombstoneParser(tombstoneStream, inAppIncludes, inAppExcludes, nativeLibraryDir)
    val event = streamParser.parse()

    val actualJson = serializeDebugMeta(event.debugMeta!!)
    val expectedJson = readGzippedResourceFile("/tombstone_debug_meta.json.gz")

    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `identifies the main thread via pid matching the thread id and normalizes its name`() {
    val tombstone =
      Tombstone.Builder()
        .pid(1000)
        .tid(2000)
        .signal(Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, false, 0, null))
        // main thread: id == pid, but the OS renamed it to the process name
        .addThread(
          TombstoneThread(
            1000,
            "io.sentry.samples.android",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x100, 0, "main", 0, "/system/lib64/libc.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            2000,
            "crashed-worker",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x200, 0, "crash", 0, "/system/lib64/libc.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .addThread(
          TombstoneThread(
            3000,
            "Thread-3",
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(BacktraceFrame(0, 0x300, 0, "work", 0, "/system/lib64/libc.so", 0, "")),
            emptyList(),
            0,
            0,
          )
        )
        .build()

    val event = parser.parse(tombstone)
    val threads = event.threads!!

    val main = threads.single { it.isMain == true }
    assertEquals(1000, main.id)
    assertEquals("main", main.name)

    val crashed = threads.single { it.isCrashed == true }
    assertEquals(2000, crashed.id)
    assertNotEquals(true, crashed.isMain)
    assertEquals("crashed-worker", crashed.name)

    val background = threads.single { it.id == 3000L }
    assertNotEquals(true, background.isMain)
    assertNotEquals(true, background.isCrashed)
    assertEquals("Thread-3", background.name)
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
