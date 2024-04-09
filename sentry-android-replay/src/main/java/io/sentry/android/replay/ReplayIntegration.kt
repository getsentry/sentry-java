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
import io.sentry.android.replay.util.gracefullyShutdown
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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ReplayIntegration(
    private val context: Context,
    private val dateProvider: ICurrentDateProvider
) : Integration, Closeable, ScreenshotRecorderCallback, ReplayController, ComponentCallbacks {

    internal companion object {
        private const val TAG = "ReplayIntegration"
    }

    private lateinit var options: SentryOptions
    private var hub: IHub? = null
    private var recorder: WindowRecorder? = null
    private var cache: ReplayCache? = null
    private val random by lazy { SecureRandom() }

    // TODO: probably not everything has to be thread-safe here
    private val isFullSession = AtomicBoolean(false)
    private val isEnabled = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private val currentReplayId = AtomicReference(SentryId.EMPTY_ID)
    private val segmentTimestamp = AtomicReference<Date>()
    private val replayStartTimestamp = AtomicLong()
    private val currentSegment = AtomicInteger(0)

    // TODO: surround with try-catch on the calling site
    private val replayExecutor by lazy {
        Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
    }

    private lateinit var recorderConfig: ScreenshotRecorderConfig

    private fun sample(rate: Double?): Boolean {
        if (rate != null) {
            return !(rate < random.nextDouble()) // bad luck
        }
        return false
    }

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

        isFullSession.set(sample(options.experimental.sessionReplay.sessionSampleRate))
        if (!isFullSession.get() &&
            !options.experimental.sessionReplay.isSessionReplayForErrorsEnabled
        ) {
            options.logger.log(INFO, "Session replay is disabled, full session was not sampled and errorSampleRate is not specified")
            return
        }

        this.hub = hub
        recorder = WindowRecorder(options, this)
        isEnabled.set(true)

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

        currentSegment.set(0)
        currentReplayId.set(SentryId())
        replayExecutor.submitSafely(options, "$TAG.replays_cleanup") {
            // clean up old replays
            options.cacheDirPath?.let { cacheDir ->
                File(cacheDir).listFiles { dir, name ->
                    // TODO: also exclude persisted replay_id from scope when implementing ANRs
                    if (name.startsWith("replay_") && !name.contains(currentReplayId.get().toString())) {
                        FileUtils.deleteRecursively(File(dir, name))
                    }
                    false
                }
            }
        }
        if (isFullSession.get()) {
            // only set replayId on the scope if it's a full session, otherwise all events will be
            // tagged with the replay that might never be sent when we're recording in buffer mode
            hub?.configureScope { it.replayId = currentReplayId.get() }
        }
        recorderConfig = ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        cache = ReplayCache(options, currentReplayId.get(), recorderConfig)

        recorder?.startRecording(recorderConfig)
        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp.set(DateUtils.getCurrentDateTime())
        replayStartTimestamp.set(dateProvider.currentTimeMillis)
        // TODO: finalize old recording if there's some left on disk and send it using the replayId from persisted scope (e.g. for ANRs)
    }

    override fun resume() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp.set(DateUtils.getCurrentDateTime())
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

        val sampled = sample(options.experimental.sessionReplay.errorSampleRate)

        val replayId = currentReplayId.get()
        // only tag event if it's a session mode or buffer mode that got sampled
        if (!replayId.equals(SentryId.EMPTY_ID) && (isFullSession.get() || sampled)) {
            // don't ask me why
            event.setTag("replayId", currentReplayId.get().toString())
        }

        if (isFullSession.get()) {
            options.logger.log(DEBUG, "Replay is already running in 'session' mode, not capturing for event %s", event.eventId)
            return
        }

        if (!sampled) {
            options.logger.log(INFO, "Replay wasn't sampled by errorSampleRate, not capturing for event %s", event.eventId)
            return
        }

        val errorReplayDuration = options.experimental.sessionReplay.errorReplayDuration
        val now = dateProvider.currentTimeMillis
        val currentSegmentTimestamp = if (cache?.frames?.isNotEmpty() == true) {
            // in buffer mode we have to set the timestamp of the first frame as the actual start
            DateUtils.getDateTime(cache!!.frames.first().timestamp)
        } else {
            DateUtils.getDateTime(now - errorReplayDuration)
        }
        val segmentId = currentSegment.get()
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.send_replay_for_event") {
            val videoDuration =
                createAndCaptureSegment(now - currentSegmentTimestamp.time, currentSegmentTimestamp, replayId, segmentId, height, width, BUFFER, hint)
            if (videoDuration != null) {
                currentSegment.getAndIncrement()
            }
            // since we're switching to session mode, even if the video is not sent for an error
            // we still set the timestamp to now, because session is technically started "now"
            segmentTimestamp.set(DateUtils.getDateTime(now))
        }

        hub?.configureScope { it.replayId = replayId }
        isFullSession.set(true)
    }

    override fun pause() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        val now = dateProvider.currentTimeMillis
        recorder?.pause()

        if (!isFullSession.get()) {
            return
        }

        val currentSegmentTimestamp = segmentTimestamp.get()
        val segmentId = currentSegment.get()
        val duration = now - currentSegmentTimestamp.time
        val replayId = currentReplayId.get()
        val height = recorderConfig.recordingHeight
        val width = recorderConfig.recordingWidth
        replayExecutor.submitSafely(options, "$TAG.pause") {
            val videoDuration =
                createAndCaptureSegment(duration, currentSegmentTimestamp, replayId, segmentId, height, width)
            if (videoDuration != null) {
                currentSegment.getAndIncrement()
            }
        }
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

    private fun createAndCaptureSegment(
        duration: Long,
        currentSegmentTimestamp: Date,
        replayId: SentryId,
        segmentId: Int,
        height: Int,
        width: Int,
        replayType: ReplayType = SESSION,
        hint: Hint? = null
    ): Long? {
        val generatedVideo = cache?.createVideoOf(
            duration,
            currentSegmentTimestamp.time,
            segmentId,
            height,
            width
        ) ?: return null

        val (video, frameCount, videoDuration) = generatedVideo
        captureReplay(
            video,
            replayId,
            currentSegmentTimestamp,
            segmentId,
            height,
            width,
            frameCount,
            videoDuration,
            replayType,
            hint
        )
        return videoDuration
    }

    private fun captureReplay(
        video: File,
        currentReplayId: SentryId,
        segmentTimestamp: Date,
        segmentId: Int,
        height: Int,
        width: Int,
        frameCount: Int,
        duration: Long,
        replayType: ReplayType,
        hint: Hint? = null
    ) {
        val replay = SentryReplayEvent().apply {
            eventId = currentReplayId
            replayId = currentReplayId
            this.segmentId = segmentId
            this.timestamp = DateUtils.getDateTime(segmentTimestamp.time + duration)
            if (segmentId == 0) {
                replayStartTimestamp = segmentTimestamp
            }
            this.replayType = replayType
            videoFile = video
        }

        val recording = ReplayRecording().apply {
            this.segmentId = segmentId
            payload = listOf(
                RRWebMetaEvent().apply {
                    this.timestamp = segmentTimestamp.time
                    this.height = height
                    this.width = width
                },
                RRWebVideoEvent().apply {
                    this.timestamp = segmentTimestamp.time
                    this.segmentId = segmentId
                    this.durationMs = duration
                    this.frameCount = frameCount
                    size = video.length()
                    frameRate = recorderConfig.frameRate
                    this.height = height
                    this.width = width
                    // TODO: support non-fullscreen windows later
                    left = 0
                    top = 0
                }
            )
        }

        hub?.captureReplay(replay, (hint ?: Hint()).apply { replayRecording = recording })
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
        replayExecutor.gracefullyShutdown(options)
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

    private class ReplayExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayIntegration-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }
}
