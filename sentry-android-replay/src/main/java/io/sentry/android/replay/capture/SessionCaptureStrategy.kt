package io.sentry.android.replay.capture

import android.graphics.Bitmap
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
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.util.Date
import java.util.concurrent.ScheduledExecutorService

internal class SessionCaptureStrategy(
  private val options: SentryOptions,
  private val scopes: IScopes?,
  private val dateProvider: ICurrentDateProvider,
  executor: ScheduledExecutorService,
  replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
) : BaseCaptureStrategy(options, scopes, dateProvider, executor, replayCacheProvider) {
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
      FileUtils.deleteRecursively(replayCacheDir)
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

  override fun onScreenshotRecorded(
    bitmap: Bitmap?,
    store: ReplayCache.(frameTimestamp: Long) -> Unit,
  ) {
    // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
    // reflecting the exact time of when it was captured
    val currentConfig = recorderConfig
    val frameTimestamp = dateProvider.currentTimeMillis
    replayExecutor.submit(
      ReplayRunnable("$TAG.add_frame") {
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

        val now = dateProvider.currentTimeMillis
        if ((now - currentSegmentTimestamp.time >= options.sessionReplay.sessionSegmentDuration)) {
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

        if ((now - replayStartTimestamp.get() >= options.sessionReplay.sessionDuration)) {
          options.replayController.stop()
          options.logger.log(INFO, "Session replay deadline exceeded (1h), stopping recording")
        }
      }
    )
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

    val now = dateProvider.currentTimeMillis
    val currentSegmentTimestamp = segmentTimestamp ?: return
    val duration = now - currentSegmentTimestamp.time
    val replayId = currentReplayId
    replayExecutor.submit(
      ReplayRunnable("$TAG.$taskName") {
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
        onSegmentCreated(segment)
      }
    )
  }
}
