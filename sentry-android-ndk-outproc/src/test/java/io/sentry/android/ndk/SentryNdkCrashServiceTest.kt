package io.sentry.android.ndk

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import com.google.common.truth.Truth.assertThat
import io.sentry.DateUtils
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.ndk.NdkOptions
import io.sentry.ndk.SentryNdk.CrashDaemon
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], shadows = [SentryShadowParcelFileDescriptor::class])
class SentryNdkCrashServiceTest {
  private val app = RuntimeEnvironment.getApplication()
  private val component = ComponentName(app, SentryNdkCrashService::class.java)
  private val options =
    SentryAndroidOptions().apply {
      dsn = "https://key@sentry.io/proj"
      cacheDirPath = app.cacheDir.absolutePath
    }

  @Test
  fun `binds without starting the service`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      val connection = SentryNdkCrashService.start(app, options)

      assertThat(connection).isNotNull()
      assertThat(shadowOf(app).boundServiceConnections).containsExactly(connection)
      assertThat(shadowOf(app).unboundServiceConnections).isEmpty()
      // robolectric records the bind intent as started, a startService call would add another
      assertThat(shadowOf(app).allStartedServices).hasSize(1)
    }
  }

  @Test
  fun `unbinds when the service does not configure`() {
    configure(false)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      assertThat(SentryNdkCrashService.start(app, options)).isNull()

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).hasSize(1)
    }
  }

  @Test
  fun `close unbinds the service`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      SentryNdk.init(app, options)
      val connection = shadowOf(app).boundServiceConnections.single()

      SentryNdk.close()

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).containsExactly(connection)
    }
  }

  @Test
  fun `init unbinds when native init fails`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      native
        .`when`<Any> { io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }
        .thenThrow(IllegalStateException())

      assertFailsWith<IllegalStateException> { SentryNdk.init(app, options) }

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).hasSize(1)
    }
  }

  @Test
  fun `init fails when the service does not bind`() {
    shadowOf(app).declareComponentUnbindable(component)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      assertFailsWith<IllegalStateException> { SentryNdk.init(app, options) }

      native.verify({ io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }, never())
    }
  }

  @Test
  fun `deletes the shared memory files of exited processes`() {
    configure(true)
    val dir = File(options.cacheDirPath, ".sentry-native").apply { mkdirs() }
    val running = File(dir, ".sentry-shm-service-1-a").apply { createNewFile() }
    val exited = File(dir, ".sentry-shm-service-2-b").apply { createNewFile() }
    val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    shadowOf(activityManager)
      .setProcesses(listOf(ActivityManager.RunningAppProcessInfo().apply { pid = 1 }))
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      SentryNdkCrashService.start(app, options)
    }

    assertThat(running.exists()).isTrue()
    assertThat(exited.exists()).isFalse()
  }

  @Test
  fun `matches the exit info of the app pid`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    addExitInfo(service, pid = 1, timestamp = 2000)
    addExitInfo(service, pid = 2, timestamp = 1000)

    assertThat(service.findMatchingExitInfo(options, Date(2000), 2)?.pid).isEqualTo(2)
  }

  @Test
  fun `ignores exit infos of other pids`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    addExitInfo(service, pid = 1, timestamp = 1000)

    assertThat(service.findMatchingExitInfo(options, Date(1000), 2)).isNull()
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [BlockingCrashDaemonShadow::class])
  fun `starts a daemon for a relaunched app while the previous one finishes`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    try {
      configureDaemon(service, pid = 1, fd = 10)
      assertThat(BlockingCrashDaemonShadow.daemons.poll(5, TimeUnit.SECONDS))
        .isEqualTo(listOf(1, 10, 11, 12, "shm-1"))

      configureDaemon(service, pid = 2, fd = 20)
      assertThat(BlockingCrashDaemonShadow.daemons.poll(5, TimeUnit.SECONDS))
        .isEqualTo(listOf(2, 20, 21, 22, "shm-2"))
    } finally {
      BlockingCrashDaemonShadow.release.countDown()
    }
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `sends the merged envelope and writes the marker`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, trace = tombstone())
    crashDaemon(envelope, CrashDaemon.SendResult.SENT)

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(CrashDaemonShadow.sends).containsExactly(listOf("${envelope.path}.merged", 2000L))
    assertThat(marker().readText()).isEqualTo("2000")
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `writes the marker when the merged envelope is kept`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, trace = tombstone())
    crashDaemon(envelope, CrashDaemon.SendResult.KEPT)

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(marker().readText()).isEqualTo("2000")
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `keeps a newer marker`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, trace = tombstone())
    crashDaemon(envelope, CrashDaemon.SendResult.SENT)
    marker().writeText("3000")

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(CrashDaemonShadow.sends).containsExactly(listOf("${envelope.path}.merged", 2000L))
    assertThat(marker().readText()).isEqualTo("3000")
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `writes no marker when the send fails`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, trace = tombstone())
    crashDaemon(envelope, CrashDaemon.SendResult.FAILED)

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(CrashDaemonShadow.sends).containsExactly(listOf("${envelope.path}.merged", 2000L))
    assertThat(marker().exists()).isFalse()
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `sends without a tombstone when the app did not crash natively`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, reason = ApplicationExitInfo.REASON_SIGNALED)
    crashDaemon(envelope, CrashDaemon.SendResult.SENT)

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(CrashDaemonShadow.sends).containsExactly(listOf(envelope.path, 2000L))
    assertThat(marker().exists()).isFalse()
  }

  @Test
  @Config(instrumentedPackages = ["io.sentry.ndk"], shadows = [CrashDaemonShadow::class])
  fun `sends without a tombstone when it does not parse`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    val envelope = writeNativeEnvelope(timestamp = 1000)
    addExitInfo(service, pid = 1, timestamp = 2000, trace = "invalid".byteInputStream())
    crashDaemon(envelope, CrashDaemon.SendResult.SENT)

    configureDaemon(service, pid = 1, fd = 10, tombstoneEnabled = true)
    awaitWorker()

    assertThat(CrashDaemonShadow.sends).containsExactly(listOf(envelope.path, 2000L))
    assertThat(marker().exists()).isFalse()
  }

  private fun addExitInfo(
    context: Context,
    pid: Int,
    timestamp: Long,
    reason: Int = ApplicationExitInfo.REASON_CRASH_NATIVE,
    trace: InputStream? = null,
  ) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    shadowOf(activityManager)
      .addApplicationExitInfo(
        ApplicationExitInfoBuilder.newBuilder()
          .setPid(pid)
          .setReason(reason)
          .setTimestamp(timestamp)
          .setTraceInputStream(trace)
          .build()
      )
  }

  private fun configureDaemon(
    service: SentryNdkCrashService,
    pid: Int,
    fd: Int,
    tombstoneEnabled: Boolean = false,
  ) {
    val configured =
      service.configureDaemon(
        "db",
        null,
        "shm-$pid",
        pid,
        0L,
        tombstoneEnabled,
        false,
        2000L,
        null,
        null,
        descriptor(fd),
        descriptor(fd + 1),
        descriptor(fd + 2),
      )
    assertThat(configured).isTrue()
  }

  private fun writeNativeEnvelope(timestamp: Long): File {
    val event = SentryEvent(DateUtils.getDateTime(timestamp)).apply { platform = "native" }
    val file = File(app.cacheDir, ".sentry-native/test.run/${event.eventId}.envelope")
    file.parentFile!!.mkdirs()
    file.outputStream().use {
      options.serializer.serialize(SentryEnvelope.from(options.serializer, event, null), it)
    }
    return file
  }

  private fun tombstone(): InputStream =
    GZIPInputStream(File("../sentry-android-core/src/test/resources/tombstone.pb.gz").inputStream())

  private fun crashDaemon(envelope: File, result: CrashDaemon.SendResult) {
    CrashDaemonShadow.envelopePath = envelope.path
    CrashDaemonShadow.result = result
    CrashDaemonShadow.sends.clear()
  }

  private fun awaitWorker() {
    Thread.getAllStackTraces()
      .keys
      .filter { it.name == "SentryNdkCrashService" }
      .forEach { it.join(5000) }
  }

  private fun marker(): File = File(app.cacheDir, AndroidEnvelopeCache.LAST_TOMBSTONE_REPORT)

  private fun descriptor(fd: Int): ParcelFileDescriptor = mock { on { detachFd() } doReturn fd }

  private fun configure(configured: Boolean) {
    val binder =
      object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
          reply?.writeNoException()
          reply?.writeInt(if (configured) 1 else 0)
          return true
        }
      }
    shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
  }
}

@Implements(io.sentry.ndk.SentryNdk::class)
class BlockingCrashDaemonShadow {
  companion object {
    val daemons = LinkedBlockingQueue<List<Any>>()
    val release = CountDownLatch(1)

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun runCrashDaemon(
      appPid: Int,
      appTid: Long,
      notifyFd: Int,
      readyFd: Int,
      messageFd: Int,
      shmPath: String,
    ): CrashDaemon? {
      daemons.add(listOf(appPid, notifyFd, readyFd, messageFd, shmPath))
      release.await(10, TimeUnit.SECONDS)
      return null
    }
  }
}

@Implements(io.sentry.ndk.SentryNdk::class)
class CrashDaemonShadow {
  companion object {
    var envelopePath = ""
    var result = CrashDaemon.SendResult.SENT
    val sends = mutableListOf<List<Any>>()

    @Suppress("unused") @Implementation @JvmStatic fun loadNativeLibraries() {}

    @Suppress("unused", "UNUSED_PARAMETER")
    @Implementation
    @JvmStatic
    fun nativeRunCrashDaemon(
      appPid: Int,
      appTid: Long,
      notifyFd: Int,
      readyFd: Int,
      messageFd: Int,
      shmPath: String,
    ): Long = 1L

    @Suppress("unused", "UNUSED_PARAMETER")
    @Implementation
    @JvmStatic
    fun nativeGetCrashEnvelopePath(handle: Long): String = envelopePath

    @Suppress("unused", "UNUSED_PARAMETER")
    @Implementation
    @JvmStatic
    fun nativeSendEnvelope(handle: Long, path: String, timeout: Long): Int {
      sends.add(listOf(path, timeout))
      return result.ordinal
    }
  }
}
