package io.sentry.android.replay.capture

import android.view.MotionEvent
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
import io.sentry.rrweb.RRWebIncrementalSnapshotEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebInteractionMoveEvent
import io.sentry.rrweb.RRWebInteractionMoveEvent.Position
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.FileUtils
import java.io.File
import java.util.Date
import java.util.LinkedList
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

        // rrweb values
        private const val TOUCH_MOVE_DEBOUNCE_THRESHOLD = 50
        private const val CAPTURE_MOVE_EVENT_THRESHOLD = 500
    }

    protected var cache: ReplayCache? = null
    protected val segmentTimestamp = AtomicReference<Date>()
    protected val replayStartTimestamp = AtomicLong()
    protected val screenAtStart = AtomicReference<String>()
    override val currentReplayId = AtomicReference(SentryId.EMPTY_ID)
    override val currentSegment = AtomicInteger(0)
    override val replayCacheDir: File? get() = cache?.replayCacheDir

    protected val currentEvents = LinkedList<RRWebEvent>()
    private val currentEventsLock = Any()
    private val currentPositions = LinkedHashMap<Int, ArrayList<Position>>(10)
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

        val urls = LinkedList<String>()
        hub?.configureScope { scope ->
            scope.breadcrumbs.forEach { breadcrumb ->
                if (breadcrumb.timestamp.time >= segmentTimestamp.time &&
                    breadcrumb.timestamp.time < endTimestamp.time
                ) {
                    val rrwebEvent = options
                        .replayController
                        .breadcrumbConverter
                        .convert(breadcrumb)

                    if (rrwebEvent != null) {
                        recordingPayload += rrwebEvent

                        // fill in the urls array from navigation breadcrumbs
                        if ((rrwebEvent as? RRWebBreadcrumbEvent)?.category == "navigation") {
                            urls.add(rrwebEvent.data!!["to"] as String)
                        }
                    }
                }
            }
        }

        if (screenAtStart.get() != null && urls.first != screenAtStart.get()) {
            urls.addFirst(screenAtStart.get())
        }

        rotateCurrentEvents(endTimestamp.time) { event ->
            if (event.timestamp >= segmentTimestamp.time) {
                recordingPayload += event
            }
        }

        val recording = ReplayRecording().apply {
            this.segmentId = segmentId
            payload = recordingPayload.sortedBy { it.timestamp }
        }

        replay.urls = urls
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
        val rrwebEvents = event.toRRWebIncrementalSnapshotEvent()
        if (rrwebEvents != null) {
            synchronized(currentEventsLock) {
                currentEvents += rrwebEvents
            }
        }
    }

    override fun close() {
        replayExecutor.gracefullyShutdown(options)
    }

    protected fun rotateCurrentEvents(
        until: Long,
        callback: ((RRWebEvent) -> Unit)? = null
    ) {
        synchronized(currentEventsLock) {
            var event = currentEvents.peek()
            while (event != null && event.timestamp < until) {
                callback?.invoke(event)
                currentEvents.remove()
                event = currentEvents.peek()
            }
        }
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

    private fun MotionEvent.toRRWebIncrementalSnapshotEvent(): List<RRWebIncrementalSnapshotEvent>? {
        val event = this
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // we only throttle move events as those can be overwhelming
                val now = dateProvider.currentTimeMillis
                if (lastCapturedMoveEvent != 0L && lastCapturedMoveEvent + TOUCH_MOVE_DEBOUNCE_THRESHOLD > now) {
                    return null
                }
                lastCapturedMoveEvent = now

                currentPositions.keys.forEach { pId ->
                    val pIndex = event.findPointerIndex(pId)

                    if (pIndex == -1) {
                        // no data for this pointer
                        return@forEach
                    }

                    // idk why but rrweb does it like dis
                    if (touchMoveBaseline == 0L) {
                        touchMoveBaseline = now
                    }

                    currentPositions[pId]!! += Position().apply {
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        timeOffset = now - touchMoveBaseline
                    }
                }

                val totalOffset = now - touchMoveBaseline
                return if (totalOffset > CAPTURE_MOVE_EVENT_THRESHOLD) {
                    val moveEvents = mutableListOf<RRWebInteractionMoveEvent>()
                    for ((pointerId, positions) in currentPositions) {
                        if (positions.isNotEmpty()) {
                            moveEvents += RRWebInteractionMoveEvent().apply {
                                this.timestamp = now
                                this.positions = positions.map { pos ->
                                    pos.timeOffset -= totalOffset
                                    pos
                                }
                                this.pointerId = pointerId
                            }
                            currentPositions[pointerId]!!.clear()
                        }
                    }
                    touchMoveBaseline = 0L
                    moveEvents
                } else {
                    null
                }
            }

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pId = event.getPointerId(event.actionIndex)
                val pIndex = event.findPointerIndex(pId)

                if (pIndex == -1) {
                    // no data for this pointer
                    return null
                }

                // new finger down - add a new pointer for tracking movement
                currentPositions[pId] = ArrayList()
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = pId
                        interactionType = InteractionType.TouchStart
                    }
                )
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val pId = event.getPointerId(event.actionIndex)
                val pIndex = event.findPointerIndex(pId)

                if (pIndex == -1) {
                    // no data for this pointer
                    return null
                }

                // finger lift up - remove the pointer from tracking
                currentPositions[pId]?.clear()
                currentPositions.remove(pId)
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = pId
                        interactionType = InteractionType.TouchEnd
                    }
                )
            }
            MotionEvent.ACTION_CANCEL -> {
                // gesture cancelled - remove all pointers from tracking
                currentPositions.clear()
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.x * recorderConfig.scaleFactorX
                        y = event.y * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = 0 // the pointerId is not used for TouchCancel, so just set it to 0
                        interactionType = InteractionType.TouchCancel
                    }
                )
            }

            else -> null
        }
    }
}
