package io.sentry.android.replay.capture

import android.graphics.Bitmap
import io.sentry.DateUtils
import io.sentry.IScopes
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.util.ReplayRunnable
import io.sentry.protocol.SentryId
import io.sentry.time.AnchoredClock
import io.sentry.time.MonotonicTicker
import io.sentry.transport.ICurrentDateProvider
import java.util.Date
import java.util.concurrent.ScheduledExecutorService

/**
 * Records a full session: segments are encoded and sent continuously, one per
 * `sessionSegmentDuration`, until the 1h `sessionDuration` deadline. Used when the session is
 * sampled by `sessionSampleRate`.
 *
 * Event-triggered [captureReplay] is a no-op here — there is no buffer to flush, so the segment
 * covering the error is sent like any other. An explicit [flush] still sends the current segment.
 * Because envelopes are in flight the whole time, `ReplayIntegration` pauses this strategy while
 * offline or rate-limited so the envelope cache doesn't overflow.
 *
 * See [BufferCaptureStrategy] for the on-error counterpart.
 */
internal class SessionCaptureStrategy
@Suppress("LongParameterList")
constructor(
  private val options: SentryOptions,
  private val scopes: IScopes?,
  dateProvider: ICurrentDateProvider,
  ticker: MonotonicTicker,
  executor: ScheduledExecutorService,
  persistingExecutor: ScheduledExecutorService,
  replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
  continuedTimeline: AnchoredClock? = null,
) :
  BaseCaptureStrategy(
    options,
    scopes,
    dateProvider,
    ticker,
    executor,
    persistingExecutor,
    replayCacheProvider,
    continuedTimeline,
  ) {
  internal companion object {
    private const val TAG = "SessionCaptureStrategy"
  }

  override fun start(segmentId: Int, replayId: SentryId, replayType: ReplayType?) {
    super.start(segmentId, replayId, replayType)
    // only set replayId on the scope if it's a full session, otherwise all events will be
    // tagged with the replay that might never be sent when we're recording in buffer mode
    scopes?.configureScope {
      it.replayId = currentReplayId
      screenAtStart = it.screen?.substringAfterLast('.')
    }
  }

  override fun pause() {
    createCurrentSegment("pause") { segment ->
      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)

        currentSegment++
      }
    }
    super.pause()
  }

  override fun stop() {
    val replayCacheDir = cache?.replayCacheDir
    createCurrentSegment("stop") { segment ->
      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)
      }
      currentSegment = -1
      ReplayCache.deleteExceptSegmentVideos(replayCacheDir)
    }
    scopes?.configureScope { it.replayId = SentryId.EMPTY_ID }
    super.stop()
  }

  override fun captureReplay(isTerminating: Boolean, onSegmentSent: (Date) -> Unit) {
    if (options.sessionReplay.isDebug) {
      options.logger.log(
        DEBUG,
        "Replay is already running in 'session' mode, not capturing for event",
      )
    }
    this.isTerminating.set(isTerminating)
  }

  override fun flush(onSegmentSent: (Date) -> Unit) {
    createCurrentSegment("flush") { segment ->
      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)
        currentSegment++
        segmentTimestamp = segment.replay.timestamp
        isFlushed = true
      }
    }
  }

  override fun onScreenshotRecorded(
    bitmap: Bitmap?,
    store: ReplayCache.(frameTimestamp: Long) -> Unit,
  ) {
    // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
    // reflecting the exact time of when it was captured
    val currentConfig = recorderConfig
    val frame = stampFrame(maxForwardJumpMillis = options.sessionReplay.sessionDuration)
    val frameTimestamp = frame.timestamp
    // a recording can't span a forward jump longer than its cap, so one ends it like the cap does
    val deadlineExceeded = replayDeadline?.hasPassed() == true || frame.jumpedTooFar
    replayExecutor.submit(
      ReplayRunnable("$TAG.add_frame") {
        // first, so the old timeline is encoded and rotated without this frame
        frame.jump?.let { closeTimeline(it, currentConfig) }
        cache?.store(frameTimestamp)

        val currentSegmentTimestamp = segmentTimestamp
        currentSegmentTimestamp
          ?: run {
            options.logger.log(DEBUG, "Segment timestamp is not set, not recording frame")
            return@ReplayRunnable
          }

        if (isTerminating.get()) {
          options.logger.log(
            DEBUG,
            "Not capturing segment, because the app is terminating, will be captured on next launch",
          )
          return@ReplayRunnable
        }

        if (currentConfig == null) {
          options.logger.log(DEBUG, "Recorder config is not set, not capturing a segment")
          return@ReplayRunnable
        }

        if (
          frameTimestamp - currentSegmentTimestamp.time >=
            options.sessionReplay.sessionSegmentDuration
        ) {
          val segment =
            createSegmentInternal(
              options.sessionReplay.sessionSegmentDuration,
              currentSegmentTimestamp,
              currentReplayId,
              currentSegment,
              currentConfig.recordingHeight,
              currentConfig.recordingWidth,
              currentConfig.frameRate,
              currentConfig.bitRate,
            )
          if (segment is ReplaySegment.Created) {
            segment.capture(scopes)
            currentSegment++
            // set next segment timestamp as close to the previous one as possible to avoid gaps
            segmentTimestamp = segment.replay.timestamp
          }
        }

        if (deadlineExceeded) {
          options.replayController.stop()
          options.logger.log(INFO, "Session replay deadline exceeded (1h), stopping recording")
        }
      }
    )
  }

  /**
   * Captures the segment up to where the old timeline ended, and starts the next on the new one.
   */
  private fun closeTimeline(jump: TimelineJump, currentConfig: ScreenshotRecorderConfig?) {
    val currentSegmentTimestamp = segmentTimestamp ?: return
    if (!isTerminating.get() && currentConfig != null) {
      val segment =
        createSegmentInternal(
          jump.oldTimelineEnd - currentSegmentTimestamp.time,
          currentSegmentTimestamp,
          currentReplayId,
          currentSegment,
          currentConfig.recordingHeight,
          currentConfig.recordingWidth,
          currentConfig.frameRate,
          currentConfig.bitRate,
          events = jump.oldEvents,
        )
      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)
        currentSegment++
      }
    }
    // frames left from the old timeline would be encoded into the new one's segments
    cache?.rotate(until = jump.oldTimelineEnd)
    segmentTimestamp = DateUtils.getDateTime(jump.newTimelineStart)
  }

  override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
    createCurrentSegment("onConfigurationChanged") { segment ->
      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)

        currentSegment++
        // set next segment timestamp as close to the previous one as possible to avoid gaps
        segmentTimestamp = segment.replay.timestamp
      }
    }

    // refresh recorder config after submitting the last segment with current config
    super.onConfigurationChanged(recorderConfig)
  }

  override fun convert(): CaptureStrategy = this

  private fun createCurrentSegment(taskName: String, onSegmentCreated: (ReplaySegment) -> Unit) {
    val currentConfig = recorderConfig
    if (currentConfig == null) {
      options.logger.log(
        DEBUG,
        "Recorder config is not set, not creating segment for task: $taskName",
      )
      return
    }

    val requestedAt = clock.nowMillis()
    val replayId = currentReplayId
    replayExecutor.submit(
      ReplayRunnable("$TAG.$taskName") {
        // Keep the request time so executor delays do not extend the segment, but read the mutable
        // timeline here so a queued natural boundary cannot make it stale.
        val currentSegmentTimestamp = segmentTimestamp ?: return@ReplayRunnable
        val segment =
          createSegmentInternal(
            requestedAt - currentSegmentTimestamp.time,
            currentSegmentTimestamp,
            replayId,
            currentSegment,
            currentConfig.recordingHeight,
            currentConfig.recordingWidth,
            currentConfig.frameRate,
            currentConfig.bitRate,
          )
        onSegmentCreated(segment)
      }
    )
  }
}
