package io.sentry.android.replay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.Integration
import io.sentry.ReplayController
import io.sentry.ReplayRecording
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.capture.BufferCaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.io.Closeable
import java.io.File
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class ReplayIntegration(
    private val context: Context,
    private val dateProvider: ICurrentDateProvider
) : Integration, Closeable, ScreenshotRecorderCallback, ReplayController, ComponentCallbacks {

    private lateinit var options: SentryOptions
    private var hub: IHub? = null
    private var recorder: WindowRecorder? = null
    private val random by lazy { SecureRandom() }

    // TODO: probably not everything has to be thread-safe here
    private val isFullSession = AtomicBoolean(false)
    private val isEnabled = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private var captureStrategy: CaptureStrategy? = null

    private lateinit var recorderConfig: ScreenshotRecorderConfig

    override fun register(hub: IHub, options: SentryOptions) {
        this.options = options

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            options.logger.log(INFO, "Session replay is only supported on API 26 and above")
            return
        }

        if (!options.experimental.sessionReplay.isSessionReplayEnabled &&
            !options.experimental.sessionReplay.isSessionReplayForErrorsEnabled
        ) {
            options.logger.log(INFO, "Session replay is disabled, no sample rate specified")
            return
        }

        isFullSession.set(random.sample(options.experimental.sessionReplay.sessionSampleRate))
        if (!isFullSession.get() &&
            !options.experimental.sessionReplay.isSessionReplayForErrorsEnabled
        ) {
            options.logger.log(INFO, "Session replay is disabled, full session was not sampled and errorSampleRate is not specified")
            return
        }

        this.hub = hub
        recorder = WindowRecorder(options, this)
        isEnabled.set(true)
        captureStrategy = if (isFullSession.get()) {
            SessionCaptureStrategy(options, hub, dateProvider)
        } else {
            BufferCaptureStrategy(options, hub, dateProvider, random)
        }

        try {
            context.registerComponentCallbacks(this)
        } catch (e: Throwable) {
            options.logger.log(INFO, "ComponentCallbacks is not available, orientation changes won't be handled by Session replay", e)
        }

        addIntegrationToSdkVersion(javaClass)
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-android-replay", BuildConfig.VERSION_NAME)
    }

    override fun isRecording() = isRecording.get()

    override fun start() {
        // TODO: add lifecycle state instead and manage it in start/pause/resume/stop
        if (!isEnabled.get()) {
            return
        }

        if (isRecording.getAndSet(true)) {
            options.logger.log(
                DEBUG,
                "Session replay is already being recorded, not starting a new one"
            )
            return
        }

        recorderConfig = ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        captureStrategy?.start()
        recorder?.startRecording(recorderConfig)
    }

    override fun resume() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        captureStrategy?.resume()
        recorder?.resume()
    }

    override fun sendReplayForEvent(event: SentryEvent, hint: Hint) {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        if (!(event.isErrored || event.isCrashed)) {
            options.logger.log(DEBUG, "Event is not error or crash, not capturing for event %s", event.eventId)
            return
        }

        captureStrategy?.sendReplayForEvent(event, hint)
        isFullSession.set(true)
        captureStrategy = captureStrategy?.convert()
    }

    override fun pause() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        recorder?.pause()
        captureStrategy?.pause()
    }

    override fun stop() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = segmentTimestamp.get()
        val segmentId = currentSegment.get()
        val duration = now - currentSegmentTimestamp.time
        val replayId = currentReplayId.get()
        val replayCacheDir = cache?.replayCacheDir
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.stop") {
            // we don't flush the segment, but we still wanna clean up the folder for buffer mode
            if (isFullSession.get()) {
                createAndCaptureSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
            }
            FileUtils.deleteRecursively(replayCacheDir)
        }

        recorder?.stopRecording()
        cache?.close()
        currentSegment.set(0)
        replayStartTimestamp.set(0)
        segmentTimestamp.set(null)
        currentReplayId.set(SentryId.EMPTY_ID)
        hub?.configureScope { it.replayId = SentryId.EMPTY_ID }
        isRecording.set(false)
    }

    override fun onScreenshotRecorded(bitmap: Bitmap) {
        // have to do it before submitting, otherwise if the queue is busy, the timestamp won't be
        // reflecting the exact time of when it was captured
        val frameTimestamp = dateProvider.currentTimeMillis
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.add_frame") {
            cache?.addFrame(bitmap, frameTimestamp)

            val now = dateProvider.currentTimeMillis
            if (isFullSession.get() &&
                (now - segmentTimestamp.get().time >= options.experimental.sessionReplay.sessionSegmentDuration)
            ) {
                val currentSegmentTimestamp = segmentTimestamp.get()
                val segmentId = currentSegment.get()
                val replayId = currentReplayId.get()

                val videoDuration =
                    createAndCaptureSegment(
                        options.experimental.sessionReplay.sessionSegmentDuration,
                        currentSegmentTimestamp,
                        replayId,
                        segmentId,
                        height,
                        width
                    )
                if (videoDuration != null) {
                    currentSegment.getAndIncrement()
                    // set next segment timestamp as close to the previous one as possible to avoid gaps
                    segmentTimestamp.set(DateUtils.getDateTime(currentSegmentTimestamp.time + videoDuration))
                }
            } else if (isFullSession.get() &&
                (now - replayStartTimestamp.get() >= options.experimental.sessionReplay.sessionDuration)
            ) {
                stop()
                options.logger.log(INFO, "Session replay deadline exceeded (1h), stopping recording")
            } else if (!isFullSession.get()) {
                cache?.rotate(now - options.experimental.sessionReplay.errorReplayDuration)
            }
        }
    }

    override fun close() {
        if (!isEnabled.get()) {
            return
        }

        try {
            context.unregisterComponentCallbacks(this)
        } catch (ignored: Throwable) {
        }
        stop()
        captureStrategy?.close()
        captureStrategy = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        recorder?.stopRecording()

        // TODO: support buffer mode and breadcrumb/rrweb_event
        if (isFullSession.get()) {
            val now = dateProvider.currentTimeMillis
            val currentSegmentTimestamp = segmentTimestamp.get()
            val segmentId = currentSegment.get()
            val duration = now - currentSegmentTimestamp.time
            val replayId = currentReplayId.get()
            val height = recorderConfig.recordingHeight
            val width = recorderConfig.recordingWidth
            replayExecutor.submitSafely(options, "$TAG.onConfigurationChanged") {
                val videoDuration =
                    createAndCaptureSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
                if (videoDuration != null) {
                    currentSegment.getAndIncrement()
                }
            }
        }

        // refresh config based on new device configuration
        recorderConfig = ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        recorder?.startRecording(recorderConfig)
    }

    override fun onLowMemory() = Unit
}
