package io.sentry.android.replay.capture

import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ReplayRecording
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.SpanDataConvention
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebIncrementalSnapshotEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebInteractionMoveEvent
import io.sentry.rrweb.RRWebInteractionMoveEvent.Position
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebSpanEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.io.File
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
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
        private val snakecasePattern = "_[a-z]".toRegex()
        private val supportedNetworkData = setOf(
            "status_code",
            "method",
            "response_content_length",
            "request_content_length",
            "http.response_content_length",
            "http.request_content_length"
        )

        // rrweb values
        private const val TOUCH_MOVE_DEBOUNCE_THRESHOLD = 50
        private const val CAPTURE_MOVE_EVENT_THRESHOLD = 500
    }

    protected var cache: ReplayCache? = null
    protected val segmentTimestamp = AtomicReference<Date>()
    protected val replayStartTimestamp = AtomicLong()
    override val currentReplayId = AtomicReference(SentryId.EMPTY_ID)
    override val currentSegment = AtomicInteger(0)
    override val replayCacheDir: File? get() = cache?.replayCacheDir

    private val currentEvents = CopyOnWriteArrayList<RRWebEvent>()
    private val currentPositions = mutableListOf<Position>()
    private var touchMoveBaseline = 0L
    private var lastCapturedMoveEvent = 0L

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
                if (breadcrumb.timestamp.time >= segmentTimestamp.time &&
                    breadcrumb.timestamp.time < endTimestamp.time
                ) {
                    var breadcrumbMessage: String? = null
                    var breadcrumbCategory: String? = null
                    var breadcrumbLevel: SentryLevel? = null
                    val breadcrumbData = mutableMapOf<String, Any?>()
                    when {
                        breadcrumb.category == "http" -> {
                            if (breadcrumb.isValidForRRWebSpan()) {
                                recordingPayload += breadcrumb.toRRWebSpanEvent()
                            }
                            return@forEach
                        }

                        breadcrumb.type == "navigation" &&
                            breadcrumb.category == "app.lifecycle" -> {
                            breadcrumbCategory = "app.${breadcrumb.data["state"]}"
                        }

                        breadcrumb.type == "navigation" &&
                            breadcrumb.category == "device.orientation" -> {
                            breadcrumbCategory = breadcrumb.category!!
                            val position = breadcrumb.data["position"]
                            if (position == "landscape" || position == "portrait") {
                                breadcrumbData["position"] = position
                            } else {
                                return@forEach
                            }
                        }

                        breadcrumb.type == "navigation" -> {
                            breadcrumbCategory = "navigation"
                            breadcrumbData["to"] = when {
                                breadcrumb.data["state"] == "resumed" -> (breadcrumb.data["screen"] as? String)?.substringAfterLast('.')
                                "to" in breadcrumb.data -> breadcrumb.data["to"] as? String
                                else -> return@forEach
                            } ?: return@forEach
                        }

                        breadcrumb.category == "ui.click" -> {
                            breadcrumbCategory = "ui.tap"
                            breadcrumbMessage = (
                                breadcrumb.data["view.id"]
                                    ?: breadcrumb.data["view.tag"]
                                    ?: breadcrumb.data["view.class"]
                                ) as? String ?: return@forEach
                            breadcrumbData.putAll(breadcrumb.data)
                        }

                        breadcrumb.type == "system" && breadcrumb.category == "network.event" -> {
                            breadcrumbCategory = "device.connectivity"
                            breadcrumbData["state"] = when {
                                breadcrumb.data["action"] == "NETWORK_LOST" -> "offline"
                                "network_type" in breadcrumb.data -> if (!(breadcrumb.data["network_type"] as? String).isNullOrEmpty()) {
                                    breadcrumb.data["network_type"]
                                } else {
                                    return@forEach
                                }
                                else -> return@forEach
                            }
                        }

                        breadcrumb.data["action"] == "BATTERY_CHANGED" -> {
                            breadcrumbCategory = "device.battery"
                            breadcrumbData.putAll(
                                breadcrumb.data.filterKeys {
                                    it == "level" || it == "charging"
                                }
                            )
                        }

                        else -> {
                            breadcrumbCategory = breadcrumb.category
                            breadcrumbMessage = breadcrumb.message
                            breadcrumbLevel = breadcrumb.level
                            breadcrumbData.putAll(breadcrumb.data)
                        }
                    }
                    if (!breadcrumbCategory.isNullOrEmpty()) {
                        recordingPayload += RRWebBreadcrumbEvent().apply {
                            timestamp = breadcrumb.timestamp.time
                            breadcrumbTimestamp = breadcrumb.timestamp.time / 1000.0
                            breadcrumbType = "default"
                            category = breadcrumbCategory
                            message = breadcrumbMessage
                            level = breadcrumbLevel
                            data = breadcrumbData
                        }
                    }
                }
            }
        }
        currentEvents.removeAll {
            if (it.timestamp > segmentTimestamp.time && it.timestamp < endTimestamp.time) {
                recordingPayload += it
            }
            it.timestamp < endTimestamp.time
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

    override fun onTouchEvent(event: MotionEvent) {
        // TODO: rotate in buffer mode
        val rrwebEvent = event.toRRWebIncrementalSnapshotEvent()
        if (rrwebEvent != null) {
            currentEvents += rrwebEvent
        }
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
            SpanDataConvention.HTTP_START_TIMESTAMP in data &&
            SpanDataConvention.HTTP_END_TIMESTAMP in data
    }

    private fun String.snakeToCamelCase(): String {
        return replace(snakecasePattern) { it.value.last().uppercase() }
    }

    private fun Breadcrumb.toRRWebSpanEvent(): RRWebSpanEvent {
        val breadcrumb = this
        return RRWebSpanEvent().apply {
            timestamp = breadcrumb.timestamp.time
            op = "resource.http"
            description = breadcrumb.data["url"] as String
            startTimestamp =
                (breadcrumb.data[SpanDataConvention.HTTP_START_TIMESTAMP] as Long) / 1000.0
            endTimestamp =
                (breadcrumb.data[SpanDataConvention.HTTP_END_TIMESTAMP] as Long) / 1000.0

            val breadcrumbData = mutableMapOf<String, Any?>()
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
    }

    private fun MotionEvent.toRRWebIncrementalSnapshotEvent(): RRWebIncrementalSnapshotEvent? {
        val event = this
        return when (val action = event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // we only throttle move events as those can be overwhelming
                val now = dateProvider.currentTimeMillis
                if (lastCapturedMoveEvent != 0L && lastCapturedMoveEvent + TOUCH_MOVE_DEBOUNCE_THRESHOLD > now) {
                    return null
                }
                lastCapturedMoveEvent = now

                // idk why but rrweb does it like dis
                if (touchMoveBaseline == 0L) {
                    touchMoveBaseline = now
                }

                currentPositions += Position().apply {
                    x = event.x * recorderConfig.scaleFactorX
                    y = event.y * recorderConfig.scaleFactorY
                    id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                    timeOffset = now - touchMoveBaseline
                }

                val totalOffset = now - touchMoveBaseline
                return if (totalOffset > CAPTURE_MOVE_EVENT_THRESHOLD) {
                    RRWebInteractionMoveEvent().apply {
                        timestamp = now
                        positions = currentPositions.map { pos ->
                            pos.timeOffset -= totalOffset
                            pos
                        }
                    }.also {
                        currentPositions.clear()
                        touchMoveBaseline = 0L
                    }
                } else {
                    null
                }
            }

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                RRWebInteractionEvent().apply {
                    timestamp = dateProvider.currentTimeMillis
                    x = event.x * recorderConfig.scaleFactorX
                    y = event.y * recorderConfig.scaleFactorY
                    id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                    interactionType = when (action) {
                        MotionEvent.ACTION_UP -> InteractionType.TouchEnd
                        MotionEvent.ACTION_DOWN -> InteractionType.TouchStart
                        MotionEvent.ACTION_CANCEL -> InteractionType.TouchCancel
                        else -> InteractionType.TouchMove_Departed // should not happen
                    }
                }
            }

            else -> null
        }
    }
}
