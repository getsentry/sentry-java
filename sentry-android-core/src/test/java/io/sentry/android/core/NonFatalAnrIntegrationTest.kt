package io.sentry.android.core

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.android.core.anr.NonFatalAnrMarker
import io.sentry.android.core.hang.HangManager
import io.sentry.android.core.hang.HangRecord
import io.sentry.android.core.hang.HangRecordingListener
import io.sentry.android.core.internal.util.AndroidThreadChecker
import io.sentry.hints.AbnormalExit
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.HintUtils
import io.sentry.util.Random
import io.sentry.util.SentryRandom
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NonFatalAnrIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private val scopes = mock<IScopes>()
  private val manager = mock<HangManager>()
  private lateinit var options: SentryAndroidOptions
  private val marker by lazy { File(tmpDir.root, "last_non_fatal_anr") }
  private var markerExistedAtCapture: Boolean? = null

  @BeforeTest
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
    options =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        nonFatalAnrSampleRate = 1.0
        hangManager = manager
      }
    whenever(scopes.captureEvent(any<SentryEvent>(), any<Hint>())).thenAnswer {
      markerExistedAtCapture = marker.exists()
      SentryId()
    }
  }

  private fun frame(cls: String, method: String) = StackTraceElement(cls, method, "F.java", 1)

  private val systemStack =
    arrayOf(
      frame("java.lang.Object", "wait"),
      frame("android.os.Handler", "dispatchMessage"),
      frame("android.os.Looper", "loop"),
    )

  private fun record(
    durationMs: Long = 1_000,
    samples: List<AnrStackTrace> =
      listOf(
        AnrStackTrace(1_100, systemStack, Thread.State.RUNNABLE),
        AnrStackTrace(1_200, systemStack, Thread.State.BLOCKED),
      ),
  ) =
    HangRecord.Builder()
      .setDurationMs(durationMs)
      .setStartTimestampMs(1_000)
      .apply { samples.forEach { addStackTrace(it) } }
      .build()

  private fun subscribed(): HangRecordingListener {
    val integration = NonFatalAnrIntegration()
    integration.register(scopes, options)
    val captor = argumentCaptor<HangRecordingListener>()
    verify(manager).subscribe(captor.capture())
    return captor.firstValue
  }

  private fun capturedHint(): NonFatalAnrHint =
    HintUtils.getSentrySdkHint(capturedEvent().second) as NonFatalAnrHint

  private fun capturedEvent(): Pair<SentryEvent, Hint> {
    val event = argumentCaptor<SentryEvent>()
    val hint = argumentCaptor<Hint>()
    verify(scopes).captureEvent(event.capture(), hint.capture())
    return event.firstValue to hint.firstValue
  }

  @Test
  fun `register subscribes when enabled`() {
    subscribed()

    assertThat(SentryIntegrationPackageStorage.getInstance().integrations).contains("NonFatalAnr")
  }

  @Test
  fun `register subscribes without a draw`() {
    // Enabled, but only a draw of exactly 0.0 is below it.
    options.nonFatalAnrSampleRate = Double.MIN_VALUE

    NonFatalAnrIntegration().register(scopes, options)

    verify(manager).subscribe(any())
    assertThat(SentryIntegrationPackageStorage.getInstance().integrations).contains("NonFatalAnr")
  }

  @Test
  fun `disabled by default does not subscribe`() {
    options.nonFatalAnrSampleRate = null

    NonFatalAnrIntegration().register(scopes, options)

    verify(manager, never()).subscribe(any())
  }

  @Test
  fun `anr disabled does not subscribe`() {
    options.isAnrEnabled = false

    NonFatalAnrIntegration().register(scopes, options)

    verify(manager, never()).subscribe(any())
  }

  @Test
  fun `no hang manager does not subscribe`() {
    options.hangManager = null

    NonFatalAnrIntegration().register(scopes, options)
    NonFatalAnrIntegration().close()

    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .doesNotContain("NonFatalAnr")
  }

  @Test
  fun `close unsubscribes the subscribed listener once`() {
    val integration = NonFatalAnrIntegration()
    integration.register(scopes, options)
    val captor = argumentCaptor<HangRecordingListener>()
    verify(manager).subscribe(captor.capture())

    integration.close()
    integration.close()

    verify(manager, times(1)).unsubscribe(captor.firstValue)
  }

  @Test
  fun `sampled hang asks for a record in memory only`() {
    val listener = subscribed()

    val request = listener.onHangStarted()!!

    assertThat(request.shouldRecord()).isTrue()
    assertThat(request.shouldPersist()).isFalse()
  }

  @Test
  fun `unsampled hang needs nothing from the hang manager`() {
    options.nonFatalAnrSampleRate = Double.MIN_VALUE
    val listener = subscribed()

    assertThat(listener.onHangStarted()).isNull()
  }

  @Test
  fun `a new random number is drawn at each hang`() {
    val draws = Random(SEED, 0).let { listOf(it.nextDouble(), it.nextDouble()) }
    assertThat(draws[0]).isLessThan(draws[1])
    options.nonFatalAnrSampleRate = draws[1]
    val listener = subscribed()
    SentryRandom.current().setSeed(SEED, 0)

    assertThat(listener.onHangStarted()).isNotNull()
    assertThat(listener.onHangStarted()).isNull()
  }

  @Test
  fun `hang after the sample rate became null needs nothing from the hang manager`() {
    val listener = subscribed()

    options.nonFatalAnrSampleRate = null

    assertThat(listener.onHangStarted()).isNull()
  }

  @Test
  fun `record below threshold sends no event and writes no marker`() {
    val listener = subscribed()

    listener.onHangRecorded(record(durationMs = 699))

    verify(scopes, never()).captureEvent(any<SentryEvent>(), any<Hint>())
    assertThat(marker.exists()).isFalse()
  }

  @Test
  fun `threshold is configurable`() {
    options.nonFatalAnrThresholdMs = 2_000
    val listener = subscribed()

    listener.onHangRecorded(record(durationMs = 1_999))

    verify(scopes, never()).captureEvent(any<SentryEvent>(), any<Hint>())
  }

  @Test
  fun `recovered hang shorter than 5 s writes no marker`() {
    val listener = subscribed()

    listener.onHangRecorded(record(durationMs = 4_999))
    capturedHint().markFlushed()

    assertThat(marker.exists()).isFalse()
  }

  @Test
  fun `recovered hang of 5 s writes the marker only once the event is on disk`() {
    val listener = subscribed()

    listener.onHangRecorded(record(durationMs = 5_000))

    assertThat(markerExistedAtCapture).isFalse()
    assertThat(marker.exists()).isFalse()
    capturedHint().markFlushed()
    val read = NonFatalAnrMarker.read(options)!!
    assertThat(read.matches(android.os.Process.myPid(), 1_000)).isTrue()
    assertThat(read.matches(android.os.Process.myPid(), 6_000 + 30_000)).isTrue()
    assertThat(read.matches(android.os.Process.myPid(), 6_000 + 30_001)).isFalse()
  }

  @Test
  fun `record cut at maxRecordingTime writes the marker once the event is on disk`() {
    val listener = subscribed()

    listener.onHangRecorded(
      HangRecord.Builder()
        .setDurationMs(8_000)
        .setStartTimestampMs(1_000)
        .addStackTrace(AnrStackTrace(1_100, systemStack))
        .setEndedAtMaxRecordingTime(true)
        .build(),
    )

    assertThat(marker.exists()).isFalse()
    capturedHint().markFlushed()
    assertThat(NonFatalAnrMarker.read(options)!!.matches(android.os.Process.myPid(), 9_000))
      .isTrue()
  }

  @Test
  fun `marker is written once when the event is flushed twice`() {
    val listener = subscribed()
    listener.onHangRecorded(record(durationMs = 5_000))
    val hint = capturedHint()
    hint.markFlushed()
    marker.delete()

    hint.markFlushed()

    assertThat(marker.exists()).isFalse()
  }

  @Test
  fun `next hang deletes the marker of this process`() {
    options.executorService = ImmediateExecutorService()
    val listener = subscribed()
    listener.onHangRecorded(record(durationMs = 5_000))
    capturedHint().markFlushed()
    assertThat(marker.exists()).isTrue()

    listener.onHangStarted()

    assertThat(marker.exists()).isFalse()
  }

  @Test
  fun `unsampled next hang also deletes the marker of this process`() {
    options.executorService = ImmediateExecutorService()
    val listener = subscribed()
    listener.onHangRecorded(record(durationMs = 5_000))
    capturedHint().markFlushed()
    options.nonFatalAnrSampleRate = null

    assertThat(listener.onHangStarted()).isNull()

    assertThat(marker.exists()).isFalse()
  }

  @Test
  fun `next hang keeps the marker of the previous process`() {
    options.executorService = ImmediateExecutorService()
    val listener = subscribed()
    NonFatalAnrMarker.write(options, 1, 1_000, 2_000)

    listener.onHangStarted()

    assertThat(marker.exists()).isTrue()
  }

  @Test
  fun `event has handled mechanism and no abnormal exit hint`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, hint) = capturedEvent()
    val exception = event.exceptions!!.single()
    assertThat(exception.mechanism!!.type).isEqualTo("NonFatalANR")
    assertThat(exception.mechanism!!.isHandled).isTrue()
    assertThat(exception.mechanism!!.data.orEmpty()).doesNotContainKey("abnormal_mechanism")
    assertThat(event.level).isEqualTo(SentryLevel.ERROR)
    assertThat(event.timestamp.time).isEqualTo(2_000)
    val typeCheckHint = HintUtils.getSentrySdkHint(hint)
    assertThat(typeCheckHint).isInstanceOf(NonFatalAnrHint::class.java)
    assertThat(typeCheckHint).isNotInstanceOf(AbnormalExit::class.java)
  }

  @Test
  fun `no samples gives one exception without stack`() {
    val listener = subscribed()

    listener.onHangRecorded(record(samples = emptyList()))

    val (event, _) = capturedEvent()
    val exception = event.exceptions!!.single()
    assertThat(exception.value).isEqualTo("Non-fatal ANR")
    assertThat(exception.mechanism!!.type).isEqualTo("NonFatalANR")
    assertThat(event.threads!!.single().state).isNull()
  }

  @Test
  fun `record cut at maxRecordingTime gives a hint with the main thread blocked`() {
    val listener = subscribed()

    listener.onHangRecorded(
      HangRecord.Builder()
        .setDurationMs(8_000)
        .setStartTimestampMs(1_000)
        .addStackTrace(AnrStackTrace(1_100, systemStack))
        .setEndedAtMaxRecordingTime(true)
        .build(),
    )

    val hint = HintUtils.getSentrySdkHint(capturedEvent().second) as NonFatalAnrHint
    assertThat(hint.isMainThreadBlocked).isTrue()
  }

  @Test
  fun `record of a recovered hang gives a hint without the main thread blocked`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val hint = HintUtils.getSentrySdkHint(capturedEvent().second) as NonFatalAnrHint
    assertThat(hint.isMainThreadBlocked).isFalse()
  }

  @Test
  fun `main thread and culprit exceptions have the main thread id`() {
    val mainThreadId = AndroidThreadChecker.getThreadId(Looper.getMainLooper().thread)
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, _) = capturedEvent()
    assertThat(event.threads!!.single().id).isEqualTo(mainThreadId)
    assertThat(event.exceptions!!.map { it.threadId }).containsExactly(mainThreadId)
  }

  @Test
  fun `exception without samples has the main thread id`() {
    val mainThreadId = AndroidThreadChecker.getThreadId(Looper.getMainLooper().thread)
    val listener = subscribed()

    listener.onHangRecorded(record(samples = emptyList()))

    val (event, _) = capturedEvent()
    assertThat(event.threads!!.single().id).isEqualTo(mainThreadId)
    assertThat(event.exceptions!!.single().threadId).isEqualTo(mainThreadId)
  }

  @Test
  fun `system-frames-only culprit gets static fingerprint`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, _) = capturedEvent()
    assertThat(event.fingerprints).containsExactly("system-frames-only-anr", "non-fatal-anr")
  }

  @Test
  fun `app frame culprit gets default fingerprint`() {
    options.addInAppInclude("com.example")
    val stack = arrayOf(frame("com.example.Foo", "bar"), frame("android.os.Looper", "loop"))
    val listener = subscribed()

    listener.onHangRecorded(record(samples = listOf(AnrStackTrace(1_100, stack))))

    val (event, _) = capturedEvent()
    assertThat(event.fingerprints).containsExactly("{{ default }}", "non-fatal-anr").inOrder()
  }

  @Test
  fun `fingerprinting disabled gets default fingerprint`() {
    options.isEnableAnrFingerprinting = false
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, _) = capturedEvent()
    assertThat(event.fingerprints).containsExactly("{{ default }}", "non-fatal-anr").inOrder()
  }

  @Test
  fun `main thread is current and has culprit thread state`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, _) = capturedEvent()
    val thread = event.threads!!.single()
    assertThat(thread.name).isEqualTo("main")
    assertThat(thread.isCurrent).isTrue()
    assertThat(thread.isMain).isTrue()
    assertThat(thread.state).isEqualTo("BLOCKED")
    assertThat(thread.stacktrace!!.frames).isNotEmpty()
  }

  @Test
  fun `thread state is taken from the last sample with the culprit stack`() {
    val other = arrayOf(frame("java.lang.Thread", "sleep"))
    val samples =
      listOf(
        AnrStackTrace(1_100, systemStack, Thread.State.RUNNABLE),
        AnrStackTrace(1_200, systemStack, Thread.State.WAITING),
        AnrStackTrace(1_300, other, Thread.State.TIMED_WAITING),
      )
    val listener = subscribed()

    listener.onHangRecorded(record(samples = samples))

    val (event, _) = capturedEvent()
    assertThat(event.threads!!.single().state).isEqualTo("WAITING")
  }

  @Test
  fun `adds context with duration and sample count`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val (event, _) = capturedEvent()
    val context = event.contexts["non_fatal_anr"] as Map<*, *>
    assertThat(context["duration_ms"]).isEqualTo(1_000L)
    assertThat(context["sample_count"]).isEqualTo(2)
  }

  @Test
  fun `adds breadcrumb`() {
    val listener = subscribed()

    listener.onHangRecorded(record())

    val captor = argumentCaptor<Breadcrumb>()
    verify(scopes).addBreadcrumb(captor.capture())
    val breadcrumb = captor.firstValue
    assertThat(breadcrumb.category).isEqualTo("anr")
    assertThat(breadcrumb.type).isEqualTo("error")
    assertThat(breadcrumb.level).isEqualTo(SentryLevel.WARNING)
    assertThat(breadcrumb.message).isEqualTo("Non-fatal ANR")
    assertThat(breadcrumb.data["duration_ms"]).isEqualTo(1_000L)
  }

  private companion object {
    const val SEED = 1L
  }
}
