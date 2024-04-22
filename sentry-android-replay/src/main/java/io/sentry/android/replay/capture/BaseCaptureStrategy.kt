package io.sentry.android.replay.capture

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ReplayRecording
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebSpanEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.io.File
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal abstract class BaseCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    protected var recorderConfig: ScreenshotRecorderConfig,
    executor: ScheduledExecutorService? = null,
    private val replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null
) : CaptureStrategy {

    internal companion object {
        private const val TAG = "CaptureStrategy"
        private val supportedNetworkData = setOf(
            "status_code",
            "method",
            "response_content_length",
            "request_content_length",
            "http.response_content_length",
            "http.request_content_length"
        )
    }

    protected var cache: ReplayCache? = null
    protected val segmentTimestamp = AtomicReference<Date>()
    protected val replayStartTimestamp = AtomicLong()
    override val currentReplayId = AtomicReference(SentryId.EMPTY_ID)
    override val currentSegment = AtomicInteger(0)
    override val replayCacheDir: File? get() = cache?.replayCacheDir

    protected val replayExecutor: ScheduledExecutorService by lazy {
        executor ?: Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
    }

    override fun start(segmentId: Int, replayId: SentryId, cleanupOldReplays: Boolean) {
        currentSegment.set(segmentId)
        currentReplayId.set(replayId)

        if (cleanupOldReplays) {
            replayExecutor.submitSafely(options, "$TAG.replays_cleanup") {
                // clean up old replays
                options.cacheDirPath?.let { cacheDir ->
                    File(cacheDir).listFiles { dir, name ->
                        // TODO: also exclude persisted replay_id from scope when implementing ANRs
                        if (name.startsWith("replay_") && !name.contains(
                                currentReplayId.get().toString()
                            )
                        ) {
                            FileUtils.deleteRecursively(File(dir, name))
                        }
                        false
                    }
                }
            }
        }

        cache =
            replayCacheProvider?.invoke(replayId) ?: ReplayCache(options, replayId, recorderConfig)

        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp.set(DateUtils.getCurrentDateTime())
        replayStartTimestamp.set(dateProvider.currentTimeMillis)
        // TODO: finalize old recording if there's some left on disk and send it using the replayId from persisted scope (e.g. for ANRs)
    }

    override fun resume() {
        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp.set(DateUtils.getCurrentDateTime())
    }

    override fun pause() = Unit

    override fun stop() {
        cache?.close()
        currentSegment.set(0)
        replayStartTimestamp.set(0)
        segmentTimestamp.set(null)
        currentReplayId.set(SentryId.EMPTY_ID)
    }

    protected fun createSegment(
        duration: Long,
        currentSegmentTimestamp: Date,
        replayId: SentryId,
        segmentId: Int,
        height: Int,
        width: Int,
        replayType: ReplayType = SESSION
    ): ReplaySegment {
        val generatedVideo = cache?.createVideoOf(
            duration,
            currentSegmentTimestamp.time,
            segmentId,
            height,
            width
        ) ?: return ReplaySegment.Failed

        val (video, frameCount, videoDuration) = generatedVideo
        return buildReplay(
            video,
            replayId,
            currentSegmentTimestamp,
            segmentId,
            height,
            width,
            frameCount,
            videoDuration,
            replayType
        )
    }

    private fun buildReplay(
        video: File,
        currentReplayId: SentryId,
        segmentTimestamp: Date,
        segmentId: Int,
        height: Int,
        width: Int,
        frameCount: Int,
        duration: Long,
        replayType: ReplayType
    ): ReplaySegment {
        val endTimestamp = DateUtils.getDateTime(segmentTimestamp.time + duration)
        val replay = SentryReplayEvent().apply {
            eventId = currentReplayId
            replayId = currentReplayId
            this.segmentId = segmentId
            this.timestamp = endTimestamp
            replayStartTimestamp = segmentTimestamp
            this.replayType = replayType
            videoFile = video
        }

        val recordingPayload = mutableListOf<RRWebEvent>()
        recordingPayload += RRWebMetaEvent().apply {
            this.timestamp = segmentTimestamp.time
            this.height = height
            this.width = width
        }
        recordingPayload += RRWebVideoEvent().apply {
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

        hub?.configureScope { scope ->
            scope.breadcrumbs.forEach { breadcrumb ->
                if (breadcrumb.timestamp.after(segmentTimestamp) &&
                    breadcrumb.timestamp.before(endTimestamp)
                ) {
                    // TODO: rework this later when aligned with iOS and frontend
                    var breadcrumbMessage: String? = null
                    val breadcrumbCategory: String?
                    val breadcrumbData = mutableMapOf<String, Any?>()
                    when {
                        breadcrumb.category == "http" -> {
                            if (!breadcrumb.isValidForRRWebSpan()) {
                                return@forEach
                            }
                            recordingPayload += RRWebSpanEvent().apply {
                                timestamp = breadcrumb.timestamp.time
                                op = "resource.xhr" // TODO: should be 'http' when supported on FE
                                description = breadcrumb.data["url"] as String
                                startTimestamp =
                                    (breadcrumb.data["start_timestamp"] as Long) / 1000.0
                                this.endTimestamp =
                                    (breadcrumb.data["end_timestamp"] as Long) / 1000.0
                                for ((key, value) in breadcrumb.data) {
                                    if (key in supportedNetworkData) {
                                        breadcrumbData[
                                            key
                                                .replace("content_length", "body_size")
                                                .substringAfter(".")
                                                .snakeToCamelCase()
                                        ] = value
                                    }
                                }
                                data = breadcrumbData
                            }
                            return@forEach
                        }

                        breadcrumb.category == "device.orientation" -> {
                            breadcrumbCategory = breadcrumb.category!!
                            breadcrumbMessage = breadcrumb.data["position"] as? String ?: ""
                        }

                        breadcrumb.type == "navigation" -> {
                            breadcrumbCategory = "navigation"
                            breadcrumbData["to"] = when {
                                breadcrumb.data["state"] == "resumed" -> breadcrumb.data["screen"] as? String
                                breadcrumb.category == "app.lifecycle" -> breadcrumb.data["state"] as? String
                                "to" in breadcrumb.data -> breadcrumb.data["to"] as? String
                                else -> return@forEach
                            } ?: return@forEach
                        }

                        breadcrumb.category in setOf("ui.click", "ui.scroll", "ui.swipe") -> {
                            breadcrumbCategory = breadcrumb.category!!
                            breadcrumbMessage = (
                                breadcrumb.data["view.id"]
                                    ?: breadcrumb.data["view.class"]
                                    ?: breadcrumb.data["view.tag"]
                                ) as? String ?: ""
                        }

                        breadcrumb.type == "system" -> {
                            breadcrumbCategory = breadcrumb.type!!
                            breadcrumbMessage =
                                breadcrumb.data.entries.joinToString() as? String ?: ""
                        }

                        else -> {
                            breadcrumbCategory = breadcrumb.category
                            breadcrumbMessage = breadcrumb.message
                        }
                    }
                    if (!breadcrumbCategory.isNullOrEmpty()) {
                        recordingPayload += RRWebBreadcrumbEvent().apply {
                            timestamp = breadcrumb.timestamp.time
                            breadcrumbTimestamp = breadcrumb.timestamp.time / 1000.0
                            breadcrumbType = "default"
                            category = breadcrumbCategory
                            message = breadcrumbMessage
                            data = breadcrumbData
                        }
                    }
                }
            }
        }

        val recording = ReplayRecording().apply {
            this.segmentId = segmentId
            payload = recordingPayload.sortedBy { it.timestamp }
        }

        return ReplaySegment.Created(
            videoDuration = duration,
            replay = replay,
            recording = recording
        )
    }

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        this.recorderConfig = recorderConfig
    }

    override fun close() {
        replayExecutor.gracefullyShutdown(options)
    }

    private class ReplayExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayIntegration-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }

    protected sealed class ReplaySegment {
        object Failed : ReplaySegment()
        data class Created(
            val videoDuration: Long,
            val replay: SentryReplayEvent,
            val recording: ReplayRecording
        ) : ReplaySegment() {
            fun capture(hub: IHub?, hint: Hint = Hint()) {
                hub?.captureReplay(replay, hint.apply { replayRecording = recording })
            }

            fun setSegmentId(segmentId: Int) {
                replay.segmentId = segmentId
                recording.payload?.forEach {
                    when (it) {
                        is RRWebVideoEvent -> it.segmentId = segmentId
                    }
                }
            }
        }
    }

    private fun Breadcrumb.isValidForRRWebSpan(): Boolean {
        return !(data["url"] as? String).isNullOrEmpty() &&
            "start_timestamp" in data &&
            "end_timestamp" in data
    }

    private fun String.snakeToCamelCase(): String {
        val pattern = "_[a-z]".toRegex()
        return replace(pattern) { it.value.last().uppercase() }
    }
}
