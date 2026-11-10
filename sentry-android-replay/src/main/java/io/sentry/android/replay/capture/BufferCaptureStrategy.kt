package io.sentry.android.replay.capture

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.view.MotionEvent
import io.sentry.DataCategory.All
import io.sentry.DataCategory.Replay
import io.sentry.DateUtils
import io.sentry.IScopes
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.Companion.rotateEvents
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.util.ReplayRunnable
import io.sentry.clientreport.DiscardReason.RATELIMIT_BACKOFF
import io.sentry.protocol.SentryId
import io.sentry.time.AnchoredClock
import io.sentry.time.MonotonicTicker
import io.sentry.transport.ICurrentDateProvider
import java.io.File
import java.util.Date
import java.util.concurrent.ScheduledExecutorService

/**
 * Records a rolling `errorReplayDuration` window: segments are encoded but held in memory, and
 * frames and segments older than the window are dropped on every screenshot. Used when the session
 * is not sampled by `sessionSampleRate` but `onErrorSampleRate` is set.
 *
 * Nothing is sent until [captureReplay] flushes the buffer for an error — sampled per error against
 * `onErrorSampleRate`, unlike session mode which samples once at start. After a successful flush
 * [convert] hands over to a [SessionCaptureStrategy] so the rest of the session is recorded live.
 *
 * Since nothing is in flight, `ReplayIntegration` deliberately keeps this strategy recording while
 * rate-limited, so the buffer stays warm for when the limit expires.
 */
@SuppressLint("UseRequiresApi")
@TargetApi(26)
internal class BufferCaptureStrategy
@Suppress("LongParameterList")
constructor(
  private val options: SentryOptions,
  private val scopes: IScopes?,
  private val dateProvider: ICurrentDateProvider,
  private val ticker: MonotonicTicker,
  executor: ScheduledExecutorService,
  persistingExecutor: ScheduledExecutorService,
  replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
) :
  BaseCaptureStrategy(
    options,
    scopes,
    dateProvider,
    ticker,
    executor,
    persistingExecutor,
    replayCacheProvider = replayCacheProvider,
  ) {
  // TODO: capture envelopes for buffered segments instead, but don't send them until buffer is
  // triggered
  private val bufferedSegments = mutableListOf<BufferedSegment>()

  internal companion object {
    private const val TAG = "BufferCaptureStrategy"
    private const val ENVELOPE_PROCESSING_DELAY: Long = 100L
  }

  override fun pause() {
    createCurrentSegment("pause", ::buffer)
    super.pause()
  }

  override fun stop() {
    val replayCacheDir = cache?.replayCacheDir
    replayExecutor.submit(
      ReplayRunnable("$TAG.stop") {
        ReplayCache.deleteExceptSegmentVideos(replayCacheDir)
        currentSegment = -1
      }
    )
    super.stop()
  }

  override fun captureReplay(isTerminating: Boolean, onSegmentSent: (Date) -> Unit) {
    if (isTerminating) {
      this.isTerminating.set(true)
      // avoid capturing replay, because the video will be malformed
      options.logger.log(
        DEBUG,
        "Not capturing replay for crashed event, will be captured on next launch",
      )
      return
    }

    if (isReplayRateLimited()) {
      // the segment envelopes would be dropped by the transport anyway, so don't waste resources
      // encoding videos that will only be discarded
      options.logger.log(INFO, "Replay is rate-limited, not capturing for event")
      // one lost event per flush, not per segment: the transport would have counted the current
      // segment plus every buffered one, but a flush only ever loses a single replay from the
      // user's perspective. Under-reporting here is preferable to making replay look like it
      // dropped data it never held.
      options.clientReportRecorder.recordLostEvent(RATELIMIT_BACKOFF, Replay)
      return
    }

    createCurrentSegment("capture_replay") { segment, _ ->
      bufferedSegments.capture()

      if (segment is ReplaySegment.Created) {
        segment.capture(scopes)
        // we only want to increment segment_id in the case of success, but currentSegment
        // might be irrelevant since we changed strategies, so in the callback we increment
        // it on the new strategy already
        onSegmentSent(segment.replay.timestamp)
      }
    }
  }

  override fun flush(onSegmentSent: (Date) -> Unit) = captureReplay(false, onSegmentSent)

  override fun onScreenshotRecorded(
    bitmap: Bitmap?,
    store: ReplayCache.(frameTimestamp: Long) -> Unit,
  ) {
    // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
    // reflecting the exact time of when it was captured
    val currentConfig = recorderConfig
    val frame = stampFrame()
    // a later frame may move the clock onto a new timeline before this task runs
    val bufferLimit = frame.timestamp - options.sessionReplay.errorReplayDuration
    replayExecutor.submit(
      ReplayRunnable("$TAG.add_frame") {
        // first, so the old timeline is encoded and rotated without this frame
        frame.jump?.let { followJump(it, currentConfig) }
        cache?.store(frame.timestamp)

        screenAtStart = cache?.rotate(bufferLimit)
        bufferedSegments.rotate()
      }
    )
  }

  /**
   * Moves the buffer onto the new timeline. What was recorded before the jump stays buffered, as a
   * segment of its own, unless the clock jumped forward by more than the session duration.
   */
  private fun followJump(jump: TimelineJump, currentConfig: ScreenshotRecorderConfig?) {
    if (jump.newTimelineStart - jump.oldTimelineEnd > options.sessionReplay.sessionDuration) {
      // an error soon after would send one replay spanning the jump, hours or years long; session
      // mode stops at the same jump instead
      bufferedSegments.discardWhere { true }
    } else {
      bufferOldTimeline(jump, currentConfig)
    }
    // frames left from the old timeline would be encoded into the new one's segments
    cache?.rotate(until = jump.oldTimelineEnd)
    // last, so a segment request can't see the new timeline before the cache is on it
    executorTimeline = jump.newTimeline
  }

  /** Buffers the frames up to where the old timeline ended as a segment of their own. */
  private fun bufferOldTimeline(jump: TimelineJump, currentConfig: ScreenshotRecorderConfig?) {
    val firstFrameTimestamp = cache?.firstFrameTimestamp()
    if (!isTerminating.get() && currentConfig != null && firstFrameTimestamp != null) {
      val segment =
        createSegmentInternal(
          jump.oldTimelineEnd - firstFrameTimestamp,
          DateUtils.getDateTime(firstFrameTimestamp),
          currentReplayId,
          currentSegment,
          currentConfig.recordingHeight,
          currentConfig.recordingWidth,
          currentConfig.frameRate,
          currentConfig.bitRate,
          events = jump.oldEvents,
        )
      buffer(segment, jump.oldTimeline)
    }
  }

  private fun buffer(segment: ReplaySegment, timeline: AnchoredClock) {
    if (segment is ReplaySegment.Created) {
      bufferedSegments += BufferedSegment(segment, timeline)
      currentSegment++
    }
  }

  override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
    createCurrentSegment("configuration_changed", ::buffer)
    super.onConfigurationChanged(recorderConfig)
  }

  override fun convert(): CaptureStrategy {
    if (isTerminating.get()) {
      options.logger.log(
        DEBUG,
        "Not converting to session mode, because the process is about to terminate",
      )
      return this
    }
    if (isReplayRateLimited()) {
      // captureReplay skipped the flush, so there is nothing to continue in session mode. Staying
      // in buffer mode keeps the rolling buffer warm, so the next error after the rate limit
      // expires can send a complete replay starting at segment 0.
      options.logger.log(DEBUG, "Not converting to session mode, because replay is rate-limited")
      return this
    }
    // we hand over replayExecutor and persistingExecutor to the new strategy to preserve order of
    // execution, and the timeline, which the segments about to be flushed are on
    val captureStrategy =
      SessionCaptureStrategy(
        options,
        scopes,
        dateProvider,
        ticker,
        replayExecutor,
        persistingExecutor,
        continuedTimeline = clock.timeline,
      )
    captureStrategy.recorderConfig = recorderConfig
    captureStrategy.start(
      segmentId = currentSegment,
      replayId = currentReplayId,
      replayType = BUFFER,
    )
    return captureStrategy
  }

  override fun onTouchEvent(event: MotionEvent) {
    super.onTouchEvent(event)
    val bufferLimit = clock.nowMillis() - options.sessionReplay.errorReplayDuration
    rotateEvents(currentEvents, bufferLimit)
  }

  private fun isReplayRateLimited(): Boolean =
    scopes?.rateLimiter?.let {
      it.isActiveForCategory(All) || it.isActiveForCategory(Replay)
    } == true

  private fun deleteFile(file: File?) {
    if (file == null) {
      return
    }
    try {
      if (!file.delete()) {
        options.logger.log(ERROR, "Failed to delete replay segment: %s", file.absolutePath)
      }
    } catch (e: Throwable) {
      options.logger.log(ERROR, e, "Failed to delete replay segment: %s", file.absolutePath)
    }
  }

  private fun MutableList<BufferedSegment>.capture() {
    var bufferedSegment = removeFirstOrNull()
    while (bufferedSegment != null) {
      bufferedSegment.segment.capture(scopes)
      bufferedSegment = removeFirstOrNull()
      // a short delay between processing envelopes to avoid bursting our server and hitting
      // another rate limit https://develop.sentry.dev/sdk/features/#additional-capabilities
      // InterruptedException will be handled by the outer try-catch
      Thread.sleep(ENVELOPE_PROCESSING_DELAY)
    }
  }

  private fun MutableList<BufferedSegment>.rotate() =
    // it can be that the buffered segment is half-way older than the buffer limit, but
    // we only drop it if its end timestamp is older
    discardWhere { it.endedMoreThan(options.sessionReplay.errorReplayDuration) }

  private fun MutableList<BufferedSegment>.discardWhere(predicate: (BufferedSegment) -> Boolean) {
    // TODO: can be a single while-loop
    var removed = false
    removeAll {
      if (predicate(it)) {
        currentSegment--
        deleteFile(it.segment.replay.videoFile)
        removed = true
        return@removeAll true
      }
      return@removeAll false
    }
    if (removed) {
      // shift segmentIds after rotating buffered segments
      forEachIndexed { index, buffered -> buffered.segment.setSegmentId(index) }
    }
  }

  /**
   * A segment held for the next error, with the timeline it was stamped on, so it ages by the time
   * that passed even after the recording moved onto another timeline.
   */
  private class BufferedSegment(
    val segment: ReplaySegment.Created,
    private val timeline: AnchoredClock,
  ) {
    fun endedMoreThan(millis: Long): Boolean =
      timeline.nowMillis() - segment.replay.timestamp.time > millis
  }

  private fun createCurrentSegment(
    taskName: String,
    onSegmentCreated: (segment: ReplaySegment, timeline: AnchoredClock) -> Unit,
  ) {
    val currentConfig = recorderConfig
    if (currentConfig == null) {
      options.logger.log(
        DEBUG,
        "Recorder config is not set, not creating segment for task: $taskName",
      )
      return
    }
    val request = SegmentRequest(::wholeBuffer)
    val replayId = currentReplayId

    replayExecutor.submit(
      ReplayRunnable("$TAG.$taskName") {
        val (window, timeline) = request.resolve()
        val (currentSegmentTimestamp, duration) = window
        val segment =
          createSegmentInternal(
            duration,
            currentSegmentTimestamp,
            replayId,
            currentSegment,
            currentConfig.recordingHeight,
            currentConfig.recordingWidth,
            currentConfig.frameRate,
            currentConfig.bitRate,
          )
        onSegmentCreated(segment, timeline)
      }
    )
  }

  /** The start and duration of a segment of the whole buffer, up to now on [timeline]. */
  private fun wholeBuffer(timeline: AnchoredClock): Pair<Date, Long> {
    val now = timeline.nowMillis()
    val start =
      cache?.firstFrameTimestamp()?.let {
        // in buffer mode we have to set the timestamp of the first frame as the actual start
        DateUtils.getDateTime(it)
      } ?: DateUtils.getDateTime(now - options.sessionReplay.errorReplayDuration)
    return start to now - start.time
  }
}
