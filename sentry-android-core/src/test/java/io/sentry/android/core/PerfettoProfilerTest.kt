package io.sentry.android.core

import android.content.Context
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
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
  fun `start returns true on first call`() {
    val profiler = getSut()
    assertTrue(profiler.start(60000))
  }

  @Test
  fun `start returns false when already started`() {
    val profiler = getSut()
    assertTrue(profiler.start(60000))
    assertFalse(profiler.start(60000))
  }

  @Test
  fun `start returns false when ProfilingManager is null`() {
    val profiler = getSut(profilingManager = null)
    assertFalse(profiler.start(60000))
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
    profiler.start(60000)

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    val result = AtomicReference<File?>()
    profiler.endAndCollect { result.set(it) }

    assertEquals(traceFile.absolutePath, result.get()?.absolutePath)
  }

  @Test
  fun `endAndCollect calls listener when result arrives later`() {
    val traceFile = createTraceFile()
    val profiler = getSut()
    profiler.start(60000)

    val result = AtomicReference<File?>()
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())

    capturedCallback.accept(mockResult(filePath = traceFile.absolutePath))

    assertEquals(traceFile.absolutePath, result.get()?.absolutePath)
  }

  @Test
  fun `endAndCollect calls listener with null on error result`() {
    val profiler = getSut()
    profiler.start(60000)

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
    profiler.start(60000)

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(errorCode = ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `timeout fires listener with null when OS never responds`() {
    val profiler = getSut()
    profiler.start(60000)

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
    profiler.start(60000)

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
    profiler.start(60000)

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
  fun `endAndCollect calls listener with null when result file path is null`() {
    val profiler = getSut()
    profiler.start(60000)

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(filePath = null))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }

  @Test
  fun `endAndCollect calls listener with null when trace file does not exist`() {
    val profiler = getSut()
    profiler.start(60000)

    val result = AtomicReference<File?>(File("sentinel"))

    capturedCallback.accept(mockResult(filePath = "/non/existent/path.pftrace"))
    profiler.endAndCollect { result.set(it) }

    assertNull(result.get())
  }
}
