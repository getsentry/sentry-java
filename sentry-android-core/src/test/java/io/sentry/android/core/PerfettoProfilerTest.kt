package io.sentry.android.core

import android.content.Context
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.SentryNanotimeDate
import io.sentry.android.core.internal.profiling.ChunkRecord
import io.sentry.profiling.ProfileRecordingState
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PerfettoProfilerTest {

  private lateinit var context: Context
  private val mockLogger = mock<ILogger>()
  private val profilerId = SentryId()
  private val executor = DeferredExecutorService()

  private lateinit var capturedCallback: Consumer<ProfilingResult>

  private val mockProfilingManager =
    mock<ProfilingManager>().also { manager ->
      doAnswer { invocation ->
          @Suppress("UNCHECKED_CAST")
          capturedCallback = invocation.getArgument(5) as Consumer<ProfilingResult>
          null
        }
        .whenever(manager)
        .requestProfiling(any(), any(), any(), any(), any(), any())
    }

  @BeforeTest
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  private fun getSut(profilingManager: ProfilingManager? = mockProfilingManager): PerfettoProfiler {
    return PerfettoProfiler(mockLogger, executor, profilingManager)
  }

  private fun PerfettoProfiler.startSession(): ChunkRecord? {
    val chunkRecord = ChunkRecord(profilerId, SentryNanotimeDate())
    return if (start(chunkRecord, 60000)) chunkRecord else null
  }

  private fun createTraceFile(): File {
    return File.createTempFile("test-trace", ".pftrace").apply {
      writeBytes(byteArrayOf(0x50, 0x65, 0x72, 0x66))
      deleteOnExit()
    }
  }

  private fun mockResult(
    errorCode: Int = ProfilingResult.ERROR_NONE,
    filePath: String? = null,
    errorMessage: String? = null,
  ): ProfilingResult {
    return mock<ProfilingResult>().also {
      whenever(it.errorCode).thenReturn(errorCode)
      whenever(it.resultFilePath).thenReturn(filePath)
      whenever(it.errorMessage).thenReturn(errorMessage)
    }
  }

  @Test
  fun `start returns a chunk record on first call`() {
    val profiler = getSut()
    assertNotNull(profiler.startSession())
  }

  @Test
  fun `start returns null when already started`() {
    val profiler = getSut()
    assertNotNull(profiler.startSession())
    assertNull(profiler.startSession())
  }

  @Test
  fun `start returns null when ProfilingManager is null`() {
    val profiler = getSut(profilingManager = null)
    assertNull(profiler.startSession())
  }

  @Test
  fun `endAndCollect calls listener with null when never started`() {
    val profiler = getSut()
    val result = AtomicReference<File?>(File("sentinel"))
    profiler.endAndCollect { result.set(it) }
    assertNull(result.get())
  }

  @Test
  fun `endAndCollect calls listener synchronously when result already available`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.startSession()

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    val result = AtomicReference<File?>()
    profiler.endAndCollect { result.set(it) }

    assertEquals(traceFile.absolutePath, result.get()?.absolutePath)
  }

  @Test
  fun `endAndCollect calls listener when result arrives later`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>()
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(traceFile.absolutePath, result.get()?.absolutePath)
  }

  @Test
  fun `endAndCollect calls listener with null on error result`() {
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(
      mockResult(errorCode = ProfilingResult.ERROR_UNKNOWN, errorMessage = "unknown error")
    )
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `endAndCollect calls listener with null on rate limit error`() {
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(errorCode = ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `timeout fires listener with null when OS never responds`() {
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>(File("sentinel"))
    profiler.endAndCollect { result.set(it) }

    assertEquals("sentinel", result.get()?.name)

    executor.runAll()

    assertNull(result.get())
  }

  @Test
  fun `timeout is no-op when result already arrived`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.startSession()

    val callCount = AtomicInteger(0)
    val result = AtomicReference<File?>()
    profiler.endAndCollect {
      callCount.incrementAndGet()
      result.set(it)
    }

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(1, callCount.get())
    assertEquals(traceFile.absolutePath, result.get()?.absolutePath)

    executor.runAll()

    assertEquals(1, callCount.get())
  }

  @Test
  fun `listener is called exactly once when result and endAndCollect race`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.startSession()

    val callCount = AtomicInteger(0)
    val latch = CountDownLatch(1)

    val resultThread = Thread {
      capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))
      latch.countDown()
    }

    profiler.endAndCollect { callCount.incrementAndGet() }
    resultThread.start()

    assertTrue(latch.await(5, TimeUnit.SECONDS))

    executor.runAll()

    assertEquals(1, callCount.get())
  }

  @Test
  fun `trace file is deleted when result arrives after the timeout`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.startSession()

    val callCount = AtomicInteger(0)
    profiler.endAndCollect { callCount.incrementAndGet() }

    executor.runAll()
    assertEquals(1, callCount.get())

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(1, callCount.get())
    assertFalse(traceFile.exists())
  }

  @Test
  fun `endAndCollect calls listener with null when result file path is null`() {
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(filePath = null))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `endAndCollect calls listener with null when trace file does not exist`() {
    val profiler = getSut()
    profiler.startSession()

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(filePath = "/non/existent/path.pftrace"))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `chunk record is marked as not recorded when the result file path is null`() {
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())

    capturedCallback.accept(mockResult(filePath = null))
    profiler.endAndCollect {}

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record is marked as not recorded when the trace file does not exist`() {
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())

    capturedCallback.accept(mockResult(filePath = "/non/existent/path.pftrace"))
    profiler.endAndCollect {}

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunkRecord.recordingState)
  }

  @Test
  fun `a result arriving after the timeout does not revive the chunk record`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())
    profiler.endAndCollect {}

    // Nothing is sent for a chunk that timed out, so a late result must not mark it as recorded
    executor.runAll()
    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record stays unknown while no result arrived`() {
    val profiler = getSut()

    val chunkRecord = assertNotNull(profiler.startSession())

    assertEquals(ProfileRecordingState.UNKNOWN, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record is marked as not recorded as soon as the OS reports an error`() {
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())

    capturedCallback.accept(mockResult(errorCode = ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS))

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record is untouched after a successful result`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(ProfileRecordingState.UNKNOWN, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record is untouched after a successful collection`() {
    // Only the caller that collects the trace file can tell that the chunk was recorded
    val traceFile = createTraceFile()
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())

    profiler.endAndCollect {}
    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(ProfileRecordingState.UNKNOWN, chunkRecord.recordingState)
  }

  @Test
  fun `chunk record is marked as not recorded when the result times out`() {
    val profiler = getSut()
    val chunkRecord = assertNotNull(profiler.startSession())
    profiler.endAndCollect {}

    assertEquals(ProfileRecordingState.UNKNOWN, chunkRecord.recordingState)

    executor.runAll()

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunkRecord.recordingState)
  }
}
