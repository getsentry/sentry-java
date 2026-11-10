package io.sentry.android.replay.capture

import android.graphics.Bitmap
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeType
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayOptions.SentryReplayQuality.HIGH
import io.sentry.android.replay.BuildConfig
import io.sentry.android.replay.DefaultReplayBreadcrumbConverter
import io.sentry.android.replay.GeneratedVideo
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_DIST
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ENVIRONMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_RELEASE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_NAME
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_VERSION
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ReplayFrame
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.maskAllImages
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebOptionsEvent
import io.sentry.time.TestMonotonicTicker
import io.sentry.transport.ICurrentDateProvider
import java.io.File
import java.util.Date
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
class SessionCaptureStrategyTest {
  @get:Rule val tmpDir = TemporaryFolder()

  internal class Fixture {
    companion object {
      const val VIDEO_DURATION = 5000L
    }

    val options =
      SentryOptions().apply {
        setReplayController(
          mock { on { breadcrumbConverter }.thenReturn(DefaultReplayBreadcrumbConverter()) }
        )
      }
    val scope = Scope(options)
    val scopes =
      mock<IScopes> {
        doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
          .whenever(it)
          .configureScope(any())
        doAnswer { (it.arguments[1] as ScopeCallback).run(scope) }
          .whenever(it)
          .configureScope(anyOrNull<ScopeType>(), any())
      }
    var persistedSegment = LinkedHashMap<String, String?>()
    val replayCache =
      mock<ReplayCache> {
        on { frames }
          .thenReturn(mutableListOf(ReplayFrame(File("1720693523997.jpg"), 1720693523997)))
        on { persistSegmentValues(any(), anyOrNull()) }
          .then { persistedSegment.put(it.arguments[0].toString(), it.arguments[1]?.toString()) }
        on {
            createVideoOf(
              anyLong(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyInt(),
              anyInt(),
              anyInt(),
              any(),
            )
          }
          .thenReturn(GeneratedVideo(File("0.mp4"), 5, VIDEO_DURATION))
      }
    val recorderConfig =
      ScreenshotRecorderConfig(
        recordingWidth = 1080,
        recordingHeight = 1920,
        scaleFactorX = 1f,
        scaleFactorY = 1f,
        frameRate = 1,
        bitRate = 20_000,
      )

    // starts at the real time, so breadcrumbs the scope stamps itself fall into the recording
    var now = System.currentTimeMillis()
    val ticker = TestMonotonicTicker()

    /** Time passing: the wall clock and the ticker move together. */
    fun elapse(millis: Long) {
      now += millis
      ticker.advance(millis, MILLISECONDS)
    }

    /** The device clock being changed: only the wall clock moves. */
    fun jumpWallClock(millis: Long) {
      now += millis
    }

    private val queuedTasks = ArrayDeque<Runnable>()

    /** Holds submitted tasks until [runQueuedTasks], like a busy replay executor. */
    val queuedExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            queuedTasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }

    fun runQueuedTasks() {
      while (queuedTasks.isNotEmpty()) {
        queuedTasks.removeFirst().run()
      }
    }

    fun mockedMotionEvent(action: Int): MotionEvent = mock {
      on { actionMasked }.thenReturn(action)
      on { getPointerId(anyInt()) }.thenReturn(0)
      on { findPointerIndex(anyInt()) }.thenReturn(0)
      on { getX(anyInt()) }.thenReturn(1f)
      on { getY(anyInt()) }.thenReturn(1f)
    }

    fun getSut(
      dateProvider: ICurrentDateProvider = ICurrentDateProvider { now },
      replayCacheDir: File? = null,
      replayExecutor: ScheduledExecutorService = mock {
        doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
          }
          .whenever(it)
          .submit(any<Runnable>())
      },
    ): SessionCaptureStrategy {
      replayCacheDir?.let { whenever(replayCache.replayCacheDir).thenReturn(it) }
      return SessionCaptureStrategy(
        options,
        scopes,
        dateProvider,
        ticker,
        replayExecutor,
        mock {
          doAnswer { invocation ->
              (invocation.arguments[0] as Runnable).run()
              null
            }
            .whenever(it)
            .submit(any<Runnable>())
        },
        replayCacheProvider = { _ -> replayCache },
      )
    }
  }

  private val fixture = Fixture()

  @Test
  fun `start sets replayId on scope for full session`() {
    val strategy = fixture.getSut()
    val replayId = SentryId()

    strategy.start(0, replayId)

    assertEquals(replayId, fixture.scope.replayId)
    assertEquals(replayId, strategy.currentReplayId)
    assertEquals(0, strategy.currentSegment)
  }

  @Test
  fun `start persists the recording metadata`() {
    fixture.options.release = "io.sentry.sample@1.0.0+1"
    fixture.options.environment = "recording-env"
    fixture.options.dist = "1"
    fixture.options.sessionReplay.sdkVersion = SdkVersion("sentry.javascript.react-native", "7.0.0")
    val strategy = fixture.getSut()

    strategy.start(0, SentryId())

    assertEquals("io.sentry.sample@1.0.0+1", fixture.persistedSegment[SEGMENT_KEY_RELEASE])
    assertEquals("recording-env", fixture.persistedSegment[SEGMENT_KEY_ENVIRONMENT])
    assertEquals("1", fixture.persistedSegment[SEGMENT_KEY_DIST])
    assertEquals("sentry.javascript.react-native", fixture.persistedSegment[SEGMENT_KEY_SDK_NAME])
    assertEquals("7.0.0", fixture.persistedSegment[SEGMENT_KEY_SDK_VERSION])
  }

  @Test
  fun `start does not persist recording metadata with line breaks`() {
    fixture.options.environment = "multi\nline"
    val strategy = fixture.getSut()

    strategy.start(0, SentryId())

    assertNull(fixture.persistedSegment[SEGMENT_KEY_ENVIRONMENT])
  }

  @Test
  fun `start persists segment values`() {
    val strategy = fixture.getSut()
    val replayId = SentryId()

    strategy.start(0, replayId)
    strategy.onConfigurationChanged(fixture.recorderConfig)

    assertEquals("0", fixture.persistedSegment[SEGMENT_KEY_ID])
    assertEquals(replayId.toString(), fixture.persistedSegment[SEGMENT_KEY_REPLAY_ID])
    assertEquals(ReplayType.SESSION.toString(), fixture.persistedSegment[SEGMENT_KEY_REPLAY_TYPE])
    assertEquals(
      fixture.recorderConfig.recordingWidth.toString(),
      fixture.persistedSegment[SEGMENT_KEY_WIDTH],
    )
    assertEquals(
      fixture.recorderConfig.recordingHeight.toString(),
      fixture.persistedSegment[SEGMENT_KEY_HEIGHT],
    )
    assertEquals(
      fixture.recorderConfig.frameRate.toString(),
      fixture.persistedSegment[SEGMENT_KEY_FRAME_RATE],
    )
    assertEquals(
      fixture.recorderConfig.bitRate.toString(),
      fixture.persistedSegment[SEGMENT_KEY_BIT_RATE],
    )
    assertTrue(fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP]?.isNotEmpty() == true)
  }

  @Test
  fun `pause creates and captures current segment`() {
    val strategy = fixture.getSut()
    strategy.start(0, SentryId())
    strategy.onConfigurationChanged(fixture.recorderConfig)

    strategy.pause()

    verify(fixture.scopes)
      .captureReplay(argThat { event -> event is SentryReplayEvent && event.segmentId == 0 }, any())
    assertEquals(1, strategy.currentSegment)
  }

  @Test
  fun `stop creates and captures current segment and clears replayId from scope`() {
    val replayId = SentryId()
    val currentReplay = File(fixture.options.cacheDirPath, "replay_$replayId").also { it.mkdirs() }

    val strategy = fixture.getSut(replayCacheDir = currentReplay)
    strategy.start(0, replayId)
    strategy.onConfigurationChanged(fixture.recorderConfig)

    strategy.stop()

    verify(fixture.scopes)
      .captureReplay(argThat { event -> event is SentryReplayEvent && event.segmentId == 0 }, any())
    assertEquals(SentryId.EMPTY_ID, fixture.scope.replayId)
    assertEquals(SentryId.EMPTY_ID, strategy.currentReplayId)
    assertEquals(-1, strategy.currentSegment)
    assertFalse(currentReplay.exists())
    verify(fixture.replayCache).close()
  }

  @Test
  fun `stop keeps the segment videos and deletes everything else`() {
    val replayId = SentryId()
    // a temporary folder, not options.cacheDirPath: the kept video would outlive the test there
    val currentReplay = tmpDir.newFolder("replay_$replayId")
    val video = File(currentReplay, "0.mp4").also { it.writeText("video") }
    val frame = File(currentReplay, "1727500000000.jpg").also { it.writeText("frame") }

    val strategy = fixture.getSut(replayCacheDir = currentReplay)
    strategy.start(0, replayId)
    strategy.onConfigurationChanged(fixture.recorderConfig)

    strategy.stop()

    assertTrue(video.exists(), "a captured segment's video is still queued for sending")
    assertFalse(frame.exists())
  }

  @Test
  fun `stop closes cache after queued replay work`() {
    val tasks = mutableListOf<Runnable>()
    val calls = mutableListOf<String>()
    val replayExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            tasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
    doAnswer { calls += "close" }.whenever(fixture.replayCache).close()
    val strategy = fixture.getSut(replayExecutor = replayExecutor)
    strategy.start()
    replayExecutor.submit(Runnable { calls += "encode" })

    strategy.stop()

    verify(fixture.replayCache, never()).close()
    tasks.forEach(Runnable::run)
    assertThat(calls).containsExactly("encode", "close").inOrder()
  }

  @Test
  fun `captureReplay does nothing for non-crashed event`() {
    val strategy = fixture.getSut()
    strategy.start()

    strategy.captureReplay(false) {}

    verify(fixture.scopes, never()).captureReplay(any(), any())
  }

  @Test
  fun `flush creates and captures current segment`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)

    strategy.flush {}

    verify(fixture.scopes)
      .captureReplay(argThat { event -> event is SentryReplayEvent && event.segmentId == 0 }, any())
    assertEquals(1, strategy.currentSegment)
    assertTrue(strategy.isFlushed)
  }

  @Test
  fun `flush uses the timeline after queued segment boundaries`() {
    val tasks = mutableListOf<Runnable>()
    val segmentIds = mutableListOf<Int>()
    val replayExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            tasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
    doAnswer {
        segmentIds += (it.arguments[0] as SentryReplayEvent).segmentId
        SentryId.EMPTY_ID
      }
      .whenever(fixture.scopes)
      .captureReplay(any(), any())
    val strategy = fixture.getSut(replayExecutor = replayExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 2)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}
    strategy.flush {}
    tasks.forEach(Runnable::run)

    assertThat(segmentIds).containsExactly(0, 1).inOrder()
  }

  @Test
  fun `flush uses request time when executor is delayed`() {
    val tasks = mutableListOf<Runnable>()
    val replayExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            tasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
    val strategy = fixture.getSut(replayExecutor = replayExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = strategy.segmentTimestamp!!.time
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration)
    val requestedAt = fixture.now

    strategy.flush {}
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration)
    tasks.forEach(Runnable::run)

    verify(fixture.replayCache)
      .createVideoOf(
        eq(requestedAt - segmentStart),
        eq(segmentStart),
        eq(0),
        any(),
        any(),
        any(),
        any(),
        any(),
      )
  }

  @Test
  fun `resume updates the timeline after a queued pause`() {
    val tasks = mutableListOf<Runnable>()
    val now = fixture.now
    val replayExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            tasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
    val strategy = fixture.getSut(replayExecutor = replayExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = Date(now - fixture.options.sessionReplay.sessionSegmentDuration)
    strategy.segmentTimestamp = segmentStart

    strategy.pause()
    strategy.resume()
    tasks.forEach(Runnable::run)

    verify(fixture.replayCache)
      .createVideoOf(
        eq(now - segmentStart.time),
        eq(segmentStart.time),
        eq(0),
        any(),
        any(),
        any(),
        any(),
        any(),
      )
    assertThat(strategy.segmentTimestamp).isNotEqualTo(segmentStart)
  }

  @Test
  fun `executor delay does not trigger a segment boundary`() {
    val tasks = mutableListOf<Runnable>()
    val replayExecutor =
      mock<ScheduledExecutorService> {
        doAnswer {
            tasks += it.arguments[0] as Runnable
            null
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
    val strategy = fixture.getSut(replayExecutor = replayExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration - 1)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}
    fixture.elapse(fixture.options.sessionReplay.sessionDuration)
    tasks.forEach(Runnable::run)

    verify(fixture.scopes, never()).captureReplay(any(), any())
    verify(fixture.options.replayController, never()).stop()
  }

  @Test
  fun `when process is crashing, onScreenshotRecorded does not create new segment`() {
    val strategy = fixture.getSut()
    strategy.start()
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.captureReplay(true) {}
    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes, never()).captureReplay(any(), any())
  }

  @Test
  fun `onScreenshotRecorded creates new segment when segment duration exceeded`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.onScreenshotRecorded(mock<Bitmap>()) { frameTimestamp ->
      assertEquals(fixture.now, frameTimestamp)
    }

    var segmentTimestamp: Date? = null
    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          segmentTimestamp = event.replayStartTimestamp
          event is SentryReplayEvent && event.segmentId == 0
        },
        any(),
      )
    assertEquals(1, strategy.currentSegment)

    segmentTimestamp!!.time = segmentTimestamp!!.time.plus(Fixture.VIDEO_DURATION)
    // timestamp should be updated with video duration
    assertEquals(
      DateUtils.getTimestamp(segmentTimestamp!!),
      fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP],
    )
  }

  @Test
  fun `onScreenshotRecorded stops replay when replay duration exceeded`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(mock<ScreenshotRecorderConfig>())
    fixture.elapse(fixture.options.sessionReplay.sessionDuration * 2)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.options.replayController).stop()
  }

  @Test
  fun `onScreenshotRecorded stops replay when the wall clock jumps forward past the session duration`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    // the device corrects its clock two hours forwards a second into the recording
    fixture.elapse(1_000)
    fixture.jumpWallClock(fixture.options.sessionReplay.sessionDuration * 2)

    var frameTimestamp: Long? = null
    strategy.onScreenshotRecorded(mock<Bitmap>()) { frameTimestamp = it }

    verify(fixture.options.replayController).stop()
    // the recording ends on its own timeline instead of sending a segment hours later
    assertThat(frameTimestamp).isEqualTo(segmentStart + 1_000)
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(segmentStart)
    verify(fixture.scopes, never()).captureReplay(any(), any())
  }

  @Test
  fun `onScreenshotRecorded does not stop replay when the wall clock jumps forward less than the session duration`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(1_000)
    fixture.jumpWallClock(fixture.options.sessionReplay.sessionDuration - 2_000)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.options.replayController, never()).stop()
  }

  @Test
  fun `onScreenshotRecorded stops replay when the wall clock steps backwards past the deadline`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(mock<ScreenshotRecorderConfig>())
    // an hour of recording, during which the device drags its clock back to before the start
    fixture.ticker.advance(fixture.options.sessionReplay.sessionDuration, MILLISECONDS)
    fixture.jumpWallClock(-1000)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.options.replayController).stop()
  }

  @Test
  fun `onConfigurationChanged creates new segment and updates config`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)

    val newConfig = fixture.recorderConfig.copy(recordingHeight = 1080, recordingWidth = 1920)
    strategy.onConfigurationChanged(newConfig)

    var segmentTimestamp: Date? = null
    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          segmentTimestamp = event.replayStartTimestamp
          event is SentryReplayEvent && event.segmentId == 0
        },
        check {
          val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
          // should still capture with the old values
          assertEquals(1920, metaEvents?.first()?.height)
          assertEquals(1080, metaEvents?.first()?.width)
        },
      )
    assertEquals(1, strategy.currentSegment)

    segmentTimestamp!!.time = segmentTimestamp!!.time.plus(Fixture.VIDEO_DURATION)
    assertEquals("1080", fixture.persistedSegment[SEGMENT_KEY_HEIGHT])
    assertEquals("1920", fixture.persistedSegment[SEGMENT_KEY_WIDTH])
    // timestamp should be updated with video duration
    assertEquals(
      DateUtils.getTimestamp(segmentTimestamp!!),
      fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP],
    )
  }

  @Test
  fun `fills replay urls from navigation breadcrumbs`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    fixture.scope.addBreadcrumb(Breadcrumb.navigation("from", "to"))

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        check { assertEquals("to", it.urls!!.first()) },
        check {
          val breadcrumbEvents =
            it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
          assertEquals("navigation", breadcrumbEvents?.first()?.category)
          assertEquals("to", breadcrumbEvents?.first()?.data?.get("to"))
        },
      )
  }

  @Test
  fun `does not throw when navigation destination is not a String`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    fixture.scope.addBreadcrumb(Breadcrumb().apply { category = "navigation" })

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        check { assertNull(it.urls?.firstOrNull()) },
        check {
          val breadcrumbEvents =
            it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
          assertEquals("navigation", breadcrumbEvents?.first()?.category)
          assertNull(breadcrumbEvents?.first()?.data?.get("to"))
        },
      )
  }

  @Test
  fun `sets screen from scope as replay url`() {
    fixture.scope.screen = "MainActivity"

    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        check { assertEquals("MainActivity", it.urls!!.first()) },
        check {
          val breadcrumbEvents =
            it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
          assertTrue(breadcrumbEvents?.isEmpty() == true)
        },
      )
  }

  @Test
  fun `replayId should be set and serialized first`() {
    val strategy = fixture.getSut()
    val replayId = SentryId()

    strategy.start(0, replayId)

    assertEquals(
      replayId.toString(),
      fixture.persistedSegment.values.first(),
      "The replayId must be set first, so when we clean up stale replays" +
        "the current replay cache folder is not being deleted.",
    )
  }

  @Test
  fun `records replay options event for segment 0`() {
    fixture.options.sessionReplay.sessionSampleRate = 1.0
    fixture.options.sessionReplay.maskAllImages = false
    fixture.options.sessionReplay.quality = HIGH
    fixture.options.sessionReplay.addMaskViewClass("my.custom.View")

    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event -> event is SentryReplayEvent && event.segmentId == 0 },
        check {
          val optionsEvent = it.replayRecording?.payload?.filterIsInstance<RRWebOptionsEvent>()!!
          assertEquals("sentry.java", optionsEvent[0].optionsPayload["nativeSdkName"])
          assertEquals(BuildConfig.VERSION_NAME, optionsEvent[0].optionsPayload["nativeSdkVersion"])

          assertEquals(null, optionsEvent[0].optionsPayload["errorSampleRate"])
          assertEquals(1.0, optionsEvent[0].optionsPayload["sessionSampleRate"])
          assertEquals(true, optionsEvent[0].optionsPayload["maskAllText"])
          assertEquals(false, optionsEvent[0].optionsPayload["maskAllImages"])
          assertEquals("high", optionsEvent[0].optionsPayload["quality"])
          assertThat(optionsEvent[0].optionsPayload["maskedViewClasses"] as Collection<*>)
            .containsExactly(
              "android.widget.TextView",
              "android.webkit.WebView",
              "android.widget.VideoView",
              "androidx.camera.view.PreviewView",
              "androidx.media3.ui.PlayerView",
              "com.google.android.exoplayer2.ui.PlayerView",
              "com.google.android.exoplayer2.ui.StyledPlayerView",
              "my.custom.View",
            )
          assertThat(optionsEvent[0].optionsPayload["unmaskedViewClasses"] as Collection<*>)
            .containsExactly("android.widget.ImageView")
        },
      )
  }

  @Test
  fun `does not record replay options event for segment above 0`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}
    verify(fixture.scopes)
      .captureReplay(argThat { event -> event is SentryReplayEvent && event.segmentId == 0 }, any())

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}
    verify(fixture.scopes)
      .captureReplay(
        argThat { event -> event is SentryReplayEvent && event.segmentId == 1 },
        check {
          val optionsEvent = it.replayRecording?.payload?.find { it is RRWebOptionsEvent }
          assertNull(optionsEvent)
        },
      )
  }

  @Test
  fun `registerTraceId includes trace IDs in next segment`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    val traceId1 = SentryId()
    val traceId2 = SentryId()
    strategy.registerTraceId(traceId1)
    strategy.registerTraceId(traceId2)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          event is SentryReplayEvent &&
            event.traceIds?.size == 2 &&
            event.traceIds!!.contains(traceId1.toString()) &&
            event.traceIds!!.contains(traceId2.toString())
        },
        any(),
      )
  }

  @Test
  fun `registerTraceId clears trace IDs after segment is created`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    val traceId = SentryId()
    strategy.registerTraceId(traceId)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          event is SentryReplayEvent && event.traceIds?.contains(traceId.toString()) == true
        },
        any(),
      )

    // trigger another segment, trace IDs should be cleared
    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          event is SentryReplayEvent && event.segmentId == 1 && event.traceIds.isNullOrEmpty()
        },
        any(),
      )
  }

  @Test
  fun `registerTraceId ignores empty trace ID`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.registerTraceId(SentryId.EMPTY_ID)

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event -> event is SentryReplayEvent && event.traceIds.isNullOrEmpty() },
        any(),
      )
  }

  @Test
  fun `registerSegmentName includes unique segment names in next segment and clears them`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.registerSegmentName("CheckoutActivity")
    strategy.registerSegmentName("CheckoutActivity")
    strategy.registerSegmentName("ProductDetailsActivity")

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          event is SentryReplayEvent &&
            event.segmentNames == listOf("CheckoutActivity", "ProductDetailsActivity")
        },
        any(),
      )

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event ->
          event is SentryReplayEvent && event.segmentId == 1 && event.segmentNames.isNullOrEmpty()
        },
        any(),
      )
  }

  @Test
  fun `registerSegmentName ignores empty names and limits names to 100`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 5)

    strategy.registerSegmentName("")
    repeat(101) { strategy.registerSegmentName("ProductActivity$it") }

    strategy.onScreenshotRecorded(mock<Bitmap>()) {}

    verify(fixture.scopes)
      .captureReplay(
        argThat { event -> event is SentryReplayEvent && event.segmentNames?.size == 100 },
        any(),
      )
  }

  private fun SessionCaptureStrategy.recordFrame() =
    onScreenshotRecorded(mock<Bitmap>()) { addFrame(File("$it.jpg"), it) }

  /** Matches the [ReplayCache.createVideoOf] call that encodes one segment. */
  private fun ReplayCache.encodedVideoOf(duration: Long, from: Long, segmentId: Int) =
    createVideoOf(eq(duration), eq(from), eq(segmentId), any(), any(), any(), any(), any())

  /** The recording payload of every captured segment, by segment id. */
  private fun capturedPayloads(count: Int): Map<Int, List<RRWebEvent>> {
    val events = argumentCaptor<SentryReplayEvent>()
    val hints = argumentCaptor<Hint>()
    verify(fixture.scopes, times(count)).captureReplay(events.capture(), hints.capture())
    return events.allValues.zip(hints.allValues).associate { (event, hint) ->
      event.segmentId to hint.replayRecording!!.payload!!
    }
  }

  @Test
  fun `without a wall-clock jump, frames and segments are stamped with the wall clock`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(segmentStart)

    fixture.elapse(1_000)
    strategy.onScreenshotRecorded(mock<Bitmap>()) { assertThat(it).isEqualTo(fixture.now) }
    fixture.elapse(4_000)
    strategy.onScreenshotRecorded(mock<Bitmap>()) { assertThat(it).isEqualTo(fixture.now) }
    verify(fixture.replayCache).encodedVideoOf(5_000, segmentStart, 0)

    fixture.elapse(2_000)
    strategy.flush {}
    val nextSegmentStart = segmentStart + Fixture.VIDEO_DURATION
    verify(fixture.replayCache).encodedVideoOf(fixture.now - nextSegmentStart, nextSegmentStart, 1)
  }

  @Test
  fun `a wall-clock jump within the tolerance is not followed`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    fixture.elapse(1_000)
    fixture.jumpWallClock(ReplayClock.JUMP_TOLERANCE_MS)

    strategy.onScreenshotRecorded(mock<Bitmap>()) { assertThat(it).isEqualTo(segmentStart + 1_000) }

    verify(fixture.scopes, never()).captureReplay(any(), any())
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(segmentStart)
  }

  @Test
  fun `a backward wall-clock jump closes the segment where the old timeline ended`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    fixture.elapse(1_000)
    strategy.recordFrame()
    fixture.elapse(2_000)
    fixture.jumpWallClock(-120_000)
    val newTimelineStart = fixture.now

    strategy.recordFrame()

    inOrder(fixture.replayCache) {
      verify(fixture.replayCache).addFrame(any<File>(), eq(segmentStart + 1_000), anyOrNull())
      verify(fixture.replayCache).encodedVideoOf(3_000, segmentStart, 0)
      verify(fixture.replayCache).rotate(eq(segmentStart + 3_000))
      // stored only once the old timeline is closed, so it is not encoded or rotated with it
      verify(fixture.replayCache).addFrame(any<File>(), eq(newTimelineStart), anyOrNull())
    }
    verify(fixture.scopes).captureReplay(argThat { segmentId == 0 }, any())
    assertThat(strategy.currentSegment).isEqualTo(1)
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(newTimelineStart)
    // what crash recovery on the next launch starts the recovered segment at
    assertThat(fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP])
      .isEqualTo(DateUtils.getTimestamp(Date(newTimelineStart)))

    repeat(4) {
      fixture.elapse(1_000)
      strategy.recordFrame()
    }
    verify(fixture.scopes, times(1)).captureReplay(any(), any())

    fixture.elapse(1_000)
    strategy.recordFrame()
    verify(fixture.replayCache).encodedVideoOf(5_000, newTimelineStart, 1)
    verify(fixture.scopes, times(2)).captureReplay(any(), any())
  }

  @Test
  fun `a forward wall-clock jump closes the segment instead of cutting frozen ones`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    fixture.elapse(3_000)
    fixture.jumpWallClock(120_000)
    val newTimelineStart = fixture.now

    strategy.recordFrame()
    repeat(4) {
      fixture.elapse(1_000)
      strategy.recordFrame()
    }

    verify(fixture.replayCache).encodedVideoOf(3_000, segmentStart, 0)
    verify(fixture.scopes, times(1)).captureReplay(any(), any())
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(newTimelineStart)

    fixture.elapse(1_000)
    strategy.recordFrame()
    verify(fixture.replayCache).encodedVideoOf(5_000, newTimelineStart, 1)
    verify(fixture.scopes, times(2)).captureReplay(any(), any())
  }

  @Test
  fun `breadcrumbs stamped after a wall-clock jump go into the segment after it`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    val segmentStart = fixture.now
    fixture.scope.addBreadcrumb(
      Breadcrumb(Date(segmentStart + 500)).apply {
        category = "test"
        message = "before"
      }
    )
    fixture.elapse(3_000)
    fixture.jumpWallClock(-120_000)
    val newTimelineStart = fixture.now
    strategy.recordFrame()

    fixture.scope.addBreadcrumb(
      Breadcrumb(Date(newTimelineStart + 500)).apply {
        category = "test"
        message = "after"
      }
    )
    fixture.elapse(5_000)
    strategy.recordFrame()

    val payloads = capturedPayloads(count = 2)
    assertThat(payloads[0]!!.filterIsInstance<RRWebBreadcrumbEvent>().map { it.message })
      .containsExactly("before")
    assertThat(payloads[1]!!.filterIsInstance<RRWebBreadcrumbEvent>().map { it.message })
      .containsExactly("after")
  }

  @Test
  fun `touch events stay with the timeline they were stamped on`() {
    val strategy = fixture.getSut(replayExecutor = fixture.queuedExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.runQueuedTasks()
    val segmentStart = fixture.now
    fixture.elapse(1_000)
    strategy.onTouchEvent(fixture.mockedMotionEvent(MotionEvent.ACTION_DOWN))
    fixture.elapse(2_000)
    fixture.jumpWallClock(-120_000)
    val newTimelineStart = fixture.now

    strategy.recordFrame()
    // stamped on the new timeline while the old one is still being closed
    fixture.elapse(100)
    strategy.onTouchEvent(fixture.mockedMotionEvent(MotionEvent.ACTION_UP))
    fixture.runQueuedTasks()
    fixture.elapse(4_900)
    strategy.recordFrame()
    fixture.runQueuedTasks()

    val payloads = capturedPayloads(count = 2)
    assertThat(payloads[0]!!.filterIsInstance<RRWebInteractionEvent>().map { it.timestamp })
      .containsExactly(segmentStart + 1_000)
    val touchesAfterJump = payloads[1]!!.filterIsInstance<RRWebInteractionEvent>()
    assertThat(touchesAfterJump.map { it.interactionType })
      .containsExactly(InteractionType.TouchEnd)
    assertThat(touchesAfterJump.map { it.timestamp }).containsExactly(newTimelineStart + 100)
  }

  @Test
  fun `executor delay across a wall-clock jump keeps both timelines apart`() {
    val strategy = fixture.getSut(replayExecutor = fixture.queuedExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.runQueuedTasks()
    val segmentStart = fixture.now
    fixture.elapse(1_000)
    strategy.recordFrame()
    fixture.elapse(2_000)
    fixture.jumpWallClock(-120_000)
    val newTimelineStart = fixture.now
    strategy.recordFrame()

    fixture.elapse(fixture.options.sessionReplay.sessionSegmentDuration * 2)
    fixture.runQueuedTasks()

    inOrder(fixture.replayCache) {
      verify(fixture.replayCache).addFrame(any<File>(), eq(segmentStart + 1_000), anyOrNull())
      verify(fixture.replayCache).encodedVideoOf(3_000, segmentStart, 0)
      verify(fixture.replayCache).addFrame(any<File>(), eq(newTimelineStart), anyOrNull())
    }
    verify(fixture.scopes, times(1)).captureReplay(any(), any())
    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(newTimelineStart)
  }

  @Test
  fun `flush queued after a wall-clock jump cuts from the new timeline`() {
    val strategy = fixture.getSut(replayExecutor = fixture.queuedExecutor)
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.runQueuedTasks()
    val segmentStart = fixture.now
    fixture.elapse(3_000)
    fixture.jumpWallClock(120_000)
    val newTimelineStart = fixture.now
    strategy.recordFrame()
    fixture.elapse(2_000)

    strategy.flush {}
    fixture.runQueuedTasks()

    verify(fixture.replayCache).encodedVideoOf(3_000, segmentStart, 0)
    verify(fixture.replayCache).encodedVideoOf(2_000, newTimelineStart, 1)
  }

  @Test
  fun `resume starts on the wall clock after it jumped while paused`() {
    val strategy = fixture.getSut()
    strategy.start()
    strategy.onConfigurationChanged(fixture.recorderConfig)
    fixture.elapse(2_000)
    strategy.pause()
    fixture.elapse(10_000)
    fixture.jumpWallClock(-120_000)

    strategy.resume()
    fixture.elapse(1_000)
    strategy.onScreenshotRecorded(mock<Bitmap>()) { assertThat(it).isEqualTo(fixture.now) }

    assertThat(strategy.segmentTimestamp!!.time).isEqualTo(fixture.now - 1_000)
    // only the paused segment, the jump needs no segment of its own
    verify(fixture.scopes, times(1)).captureReplay(any(), any())
  }
}
