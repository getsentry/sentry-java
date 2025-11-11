package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.PerformanceCollectionData
import io.sentry.SentryExecutorService
import io.sentry.SentryLevel
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.test.getCtor
import io.sentry.test.getProperty
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidProfilerTest {
  private lateinit var context: Context

  private val className = "io.sentry.android.core.AndroidProfiler"
  private val ctorTypes =
    arrayOf(
      String::class.java,
      Int::class.java,
      SentryFrameMetricsCollector::class.java,
      ISentryExecutorService::class.java,
      ILogger::class.java,
    )
  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val mockLogger = mock<ILogger>()
    var lastScheduledRunnable: Runnable? = null
    val mockExecutorService =
      object : ISentryExecutorService {
        override fun submit(runnable: Runnable): Future<*> {
          runnable.run()
          return FutureTask {}
        }

        override fun <T> submit(callable: Callable<T>): Future<T> {
          val futureTask = mock<FutureTask<T>>()
          whenever(futureTask.get()).thenAnswer {
            return@thenAnswer try {
              callable.call()
            } catch (e: Exception) {
              null
            }
          }
          return futureTask
        }

        override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
          lastScheduledRunnable = runnable
          return FutureTask {}
        }

        override fun close(timeoutMillis: Long) {}

        override fun isClosed() = false

        override fun prewarm() = Unit
      }

    val options =
      spy(SentryAndroidOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
        executorService = mockExecutorService
      }

    val frameMetricsCollector: SentryFrameMetricsCollector = mock()

    fun getSut(interval: Int = 1): AndroidProfiler =
      AndroidProfiler(
        options.profilingTracesDirPath!!,
        interval,
        frameMetricsCollector,
        options.executorService,
        options.logger,
      )
  }

  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    val buildInfoProvider = BuildInfoProvider(fixture.mockLogger)
    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, fixture.options)
    AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
      fixture.options,
      context,
      fixture.mockLogger,
      buildInfoProvider,
    )

    AndroidOptionsInitializer.installDefaultIntegrations(
      context,
      fixture.options,
      buildInfoProvider,
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
      fixture.options,
      context,
      buildInfoProvider,
      loadClass,
      activityFramesTracker,
      false,
    )
    // Profiler doesn't start if the folder doesn't exists.
    // Usually it's generated when calling Sentry.init, but for tests we can create it manually.
    File(fixture.options.profilingTracesDirPath!!).mkdirs()
  }

  @AfterTest
  fun clear() {
    context.cacheDir.deleteRecursively()
  }

  @Test
  fun `when null param is provided, invalid argument is thrown`() {
    val ctor = className.getCtor(ctorTypes)

    assertFailsWith<IllegalArgumentException> {
      ctor.newInstance(
        arrayOf(null, 0, mock(), mock<SentryExecutorService>(), mock<AndroidLogger>())
      )
    }
    assertFailsWith<IllegalArgumentException> {
      ctor.newInstance(
        arrayOf("mock", 0, null, mock<SentryExecutorService>(), mock<AndroidLogger>())
      )
    }
    assertFailsWith<IllegalArgumentException> {
      ctor.newInstance(arrayOf("mock", 0, mock(), null, mock<AndroidLogger>()))
    }
    assertFailsWith<IllegalArgumentException> {
      ctor.newInstance(arrayOf("mock", 0, mock(), mock<SentryExecutorService>(), null))
    }
  }

  @Test
  fun `profiler returns start and end timestamps`() {
    val profiler = fixture.getSut()
    val startData = profiler.start()
    val endData = profiler.endAndCollect(false, null)
    assertNotNull(startData?.startNanos)
    assertNotNull(startData?.startCpuMillis)
    assertNotNull(startData?.startTimestamp)
    assertNotNull(endData?.endNanos)
    assertNotNull(endData?.endCpuMillis)
  }

  @Test
  fun `profiler returns timeout flag`() {
    val profiler = fixture.getSut()
    profiler.start()
    val endData = profiler.endAndCollect(false, null)
    assertNotNull(endData?.didTimeout)
  }

  @Test
  fun `profiler on interval 0`() {
    val profiler = fixture.getSut(0)
    val startData = profiler.start()
    val endData = profiler.endAndCollect(false, null)
    assertNull(startData)
    assertNull(endData)
  }

  @Test
  fun `profiler does not throw if traces cannot be written to disk`() {
    File(fixture.options.profilingTracesDirPath!!).setWritable(false)
    val profiler = fixture.getSut()
    profiler.start()
    profiler.endAndCollect(false, null)
    // We assert that no trace files are written
    assertTrue(File(fixture.options.profilingTracesDirPath!!).list()!!.isEmpty())
    verify(fixture.mockLogger)
      .log(eq(SentryLevel.ERROR), eq("Error while stopping profiling: "), any())
  }

  @Test
  fun `endAndCollect works only if previously started`() {
    val profiler = fixture.getSut()
    val endData = profiler.endAndCollect(false, null)
    assertNull(endData)
  }

  @Test
  fun `timedOutData is not recorded`() {
    val profiler = fixture.getSut()

    // Start and finish first transaction profiling
    profiler.start()

    // Set timed out data by calling the timeout scheduled job
    fixture.lastScheduledRunnable?.run()

    // First transaction finishes: timed out data is returned
    val endData = profiler.endAndCollect(false, null)
    assertNull(endData)
  }

  @Test
  fun `subsequent start call doesn't do anything`() {
    val profiler = fixture.getSut()

    val startData = profiler.start()
    val notStartedData = profiler.start()

    assertNotNull(startData)
    assertNull(notStartedData)
    verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("Profiling has already started..."))
  }

  @Test
  fun `profiler starts collecting frame metrics on start`() {
    val profiler = fixture.getSut()
    profiler.start()
    verify(fixture.frameMetricsCollector, times(1)).startCollection(any())
  }

  @Test
  fun `profiler stops collecting frame metrics on end`() {
    val profiler = fixture.getSut()
    val frameMetricsCollectorId = "id"
    whenever(fixture.frameMetricsCollector.startCollection(any()))
      .thenReturn(frameMetricsCollectorId)
    profiler.start()
    profiler.endAndCollect(false, null)
    verify(fixture.frameMetricsCollector).stopCollection(frameMetricsCollectorId)
  }

  @Test
  fun `profiler does not includes performance measurements when null is passed`() {
    val profiler = fixture.getSut()
    profiler.start()
    val data = profiler.endAndCollect(false, null)
    assertFalse(data!!.measurementsMap.containsKey(ProfileMeasurement.ID_MEMORY_FOOTPRINT))
    assertFalse(data.measurementsMap.containsKey(ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT))
    assertFalse(data.measurementsMap.containsKey(ProfileMeasurement.ID_CPU_USAGE))
  }

  @Test
  fun `profiler includes performance measurements when passed on end`() {
    val profiler = fixture.getSut()
    val performanceCollectionData = ArrayList<PerformanceCollectionData>()
    var singleData = PerformanceCollectionData(10)
    singleData.usedHeapMemory = 2
    singleData.usedNativeMemory = 3
    singleData.cpuUsagePercentage = 1.4
    performanceCollectionData.add(singleData)

    singleData = PerformanceCollectionData(20)
    singleData.usedHeapMemory = 3
    singleData.usedNativeMemory = 4
    performanceCollectionData.add(singleData)

    profiler.start()
    val data = profiler.endAndCollect(false, performanceCollectionData)
    assertContentEquals(
      listOf(1.4),
      data!!.measurementsMap[ProfileMeasurement.ID_CPU_USAGE]!!.values.map { it.value },
    )
    assertContentEquals(
      listOf(2.0, 3.0),
      data.measurementsMap[ProfileMeasurement.ID_MEMORY_FOOTPRINT]!!.values.map { it.value },
    )
    assertContentEquals(
      listOf(3.0, 4.0),
      data.measurementsMap[ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT]!!.values.map { it.value },
    )
  }

  @Test
  fun `profiler stops profiling, clear running flag and scheduled job on close`() {
    val profiler = fixture.getSut()
    profiler.start()
    assert(profiler.getProperty("isRunning"))

    profiler.close()
    assertFalse(profiler.getProperty("isRunning"))

    // The timeout scheduled job should be cleared
    val scheduledJob = profiler.getProperty<Future<*>?>("scheduledFinish")
    assertNull(scheduledJob)

    // Calling transaction finish returns null, as the profiler was stopped
    val profilingTraceData = profiler.endAndCollect(false, null)
    assertNull(profilingTraceData)
  }
}
