package io.sentry

import io.sentry.cache.EnvelopeCache.CRASH_MARKER_FILE
import io.sentry.cache.EnvelopeCache.NATIVE_CRASH_MARKER_FILE
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryCrashLastRunStateTest {
  private val sentryCrashLastRunState = SentryCrashLastRunState.getInstance()

  private lateinit var file: File

  @BeforeTest
  fun `before test`() {
    file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    sentryCrashLastRunState.reset()
  }

  @AfterTest
  fun shutdown() {
    file.deleteRecursively()
  }

  @Test
  fun `reset returns null if no cacheDirPath given`() {
    assertNull(sentryCrashLastRunState.isCrashedLastRun(null, false))
  }

  @Test
  fun `returns true if Java Marker exists`() {
    val javaMarker = File(file.absolutePath, CRASH_MARKER_FILE)
    javaMarker.createNewFile()

    assertTrue(sentryCrashLastRunState.isCrashedLastRun(file.absolutePath, false)!!)
    assertFalse(javaMarker.exists())
  }

  @Test
  fun `returns true if Native Marker exists`() {
    val nativeMarker = File(file.absolutePath, NATIVE_CRASH_MARKER_FILE)
    nativeMarker.mkdirs()
    nativeMarker.createNewFile()

    assertTrue(sentryCrashLastRunState.isCrashedLastRun(file.absolutePath, false)!!)
    assertTrue(nativeMarker.exists())
  }

  @Test
  fun `returns true if Native Marker exists and should delete the file`() {
    val nativeMarker = File(file.absolutePath, NATIVE_CRASH_MARKER_FILE)
    nativeMarker.mkdirs()
    nativeMarker.createNewFile()

    assertTrue(sentryCrashLastRunState.isCrashedLastRun(file.absolutePath, true)!!)
    assertFalse(nativeMarker.exists())
  }

  @Test
  fun `returns cached value if already checked`() {
    sentryCrashLastRunState.setCrashedLastRun(true)

    assertTrue(sentryCrashLastRunState.isCrashedLastRun(file.absolutePath, false)!!)
  }
}
