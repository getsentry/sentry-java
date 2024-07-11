package io.sentry.android.replay.capture

import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ReplayRecording
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_RECORDING
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_SCREEN_AT_START
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.submitSafely
import io.sentry.cache.PersistingScopeObserver
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
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
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal abstract class BaseCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    executor: ScheduledExecutorService? = null,
    private val replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null
) : CaptureStrategy {

    internal companion object {
        private const val TAG = "CaptureStrategy"

        // rrweb values
        private const val TOUCH_MOVE_DEBOUNCE_THRESHOLD = 50
        private const val CAPTURE_MOVE_EVENT_THRESHOLD = 500
    }

    private val persistingExecutor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor(ReplayPersistingExecutorServiceThreadFactory())
    }

    protected var cache: ReplayCache? = null
    protected var recorderConfig: ScreenshotRecorderConfig by persistableAtomic { _, _, newValue ->
        if (newValue == null) {
            // recorderConfig is only nullable on init, but never after
            return@persistableAtomic
        }
        cache?.persistSegmentValues(SEGMENT_KEY_HEIGHT, newValue.recordingHeight.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_WIDTH, newValue.recordingWidth.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_FRAME_RATE, newValue.frameRate.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_BIT_RATE, newValue.bitRate.toString())
    }
    protected var segmentTimestamp by persistableAtomicNullable<Date>(propertyName = SEGMENT_KEY_TIMESTAMP) { _, _, newValue ->
        cache?.persistSegmentValues(SEGMENT_KEY_TIMESTAMP, if (newValue == null) null else DateUtils.getTimestamp(newValue))
    }
    protected val replayStartTimestamp = AtomicLong()
    protected var screenAtStart by persistableAtomicNullable<String>(propertyName = SEGMENT_KEY_REPLAY_SCREEN_AT_START)
    override var currentReplayId: SentryId by persistableAtomic(initialValue = SentryId.EMPTY_ID, propertyName = SEGMENT_KEY_REPLAY_ID)
    override var currentSegment: Int by persistableAtomic(initialValue = -1, propertyName = SEGMENT_KEY_ID)
    override val replayCacheDir: File? get() = cache?.replayCacheDir

    private var replayType by persistableAtomic<ReplayType>(propertyName = SEGMENT_KEY_REPLAY_TYPE)
    protected val currentEvents: LinkedList<RRWebEvent> = PersistableLinkedList(
        propertyName = SEGMENT_KEY_REPLAY_RECORDING,
        options,
        persistingExecutor,
        cacheProvider = { cache }
    )
    private val currentEventsLock = Any()
    private val currentPositions = LinkedHashMap<Int, ArrayList<Position>>(10)
    private var touchMoveBaseline = 0L
    private var lastCapturedMoveEvent = 0L

    protected val replayExecutor: ScheduledExecutorService by lazy {
        executor ?: Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
    }

    override fun start(recorderConfig: ScreenshotRecorderConfig, segmentId: Int, replayId: SentryId, cleanupOldReplays: Boolean) {
        if (cleanupOldReplays) {
            replayExecutor.submitSafely(options, "$TAG.replays_cleanup") {
                val unfinishedReplayId = PersistingScopeObserver.read(options, REPLAY_FILENAME, String::class.java) ?: ""
                // clean up old replays
                options.cacheDirPath?.let { cacheDir ->
                    File(cacheDir).listFiles { dir, name ->
                        if (name.startsWith("replay_") &&
                            !name.contains(replayId.toString()) &&
                            !name.contains(unfinishedReplayId)
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

        replayType = if (this is SessionCaptureStrategy) SESSION else BUFFER
        this.recorderConfig = recorderConfig
        currentSegment = segmentId
        currentReplayId = replayId

        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp = DateUtils.getCurrentDateTime()
        replayStartTimestamp.set(dateProvider.currentTimeMillis)

        finalizePreviousReplay()
    }

    override fun resume() {
        // TODO: replace it with dateProvider.currentTimeMillis to also test it
        segmentTimestamp = DateUtils.getCurrentDateTime()
    }

    override fun pause() = Unit

    override fun stop() {
        cache?.close()
        currentSegment = -1
        replayStartTimestamp.set(0)
        segmentTimestamp = null
        currentReplayId = SentryId.EMPTY_ID
    }

    protected fun createSegment(
        duration: Long,
        currentSegmentTimestamp: Date,
        replayId: SentryId,
        segmentId: Int,
        height: Int,
        width: Int,
        replayType: ReplayType = SESSION,
        cache: ReplayCache? = this.cache,
        frameRate: Int = recorderConfig.frameRate,
        screenAtStart: String? = this.screenAtStart,
        breadcrumbs: List<Breadcrumb>? = null,
        events: LinkedList<RRWebEvent> = this.currentEvents
    ): ReplaySegment {
        val generatedVideo = cache?.createVideoOf(
            duration,
            currentSegmentTimestamp.time,
            segmentId,
            height,
            width
        ) ?: return ReplaySegment.Failed

        val (video, frameCount, videoDuration) = generatedVideo

        val replayBreadcrumbs: List<Breadcrumb> = if (breadcrumbs == null) {
            var crumbs = emptyList<Breadcrumb>()
            hub?.configureScope { scope ->
                crumbs = ArrayList(scope.breadcrumbs)
            }
            crumbs
        } else {
            breadcrumbs
        }

        return buildReplay(
            video,
            replayId,
            currentSegmentTimestamp,
            segmentId,
            height,
            width,
            frameCount,
            frameRate,
            videoDuration,
            replayType,
            screenAtStart,
            replayBreadcrumbs,
            events
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
        frameRate: Int,
        duration: Long,
        replayType: ReplayType,
        screenAtStart: String?,
        breadcrumbs: List<Breadcrumb>,
        events: LinkedList<RRWebEvent>
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
            this.frameRate = frameRate
            this.height = height
            this.width = width
            // TODO: support non-fullscreen windows later
            left = 0
            top = 0
        }

        val urls = LinkedList<String>()
        breadcrumbs.forEach { breadcrumb ->
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

        if (screenAtStart != null && urls.first != screenAtStart) {
            urls.addFirst(screenAtStart)
        }

        rotateEvents(events, endTimestamp.time) { event ->
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

    protected fun rotateEvents(
        events: LinkedList<RRWebEvent>,
        until: Long,
        callback: ((RRWebEvent) -> Unit)? = null
    ) {
        synchronized(currentEventsLock) {
            var event = events.peek()
            while (event != null && event.timestamp < until) {
                callback?.invoke(event)
                events.remove()
                event = events.peek()
            }
        }
    }

    private fun finalizePreviousReplay() {
        // TODO: run it on options.executorService and read persisted options/scope values form the
        // TODO: previous run and set them directly to the ReplayEvent so they don't get overwritten in MainEventProcessor

        replayExecutor.submitSafely(options, "$TAG.finalize_previous_replay") {
            val previousReplayIdString = PersistingScopeObserver.read(options, REPLAY_FILENAME, String::class.java) ?: return@submitSafely
            val previousReplayId = SentryId(previousReplayIdString)
            if (previousReplayId == SentryId.EMPTY_ID) {
                return@submitSafely
            }
            val breadcrumbs = PersistingScopeObserver.read(options, BREADCRUMBS_FILENAME, List::class.java, Breadcrumb.Deserializer()) as? List<Breadcrumb>

            val lastSegment = ReplayCache.fromDisk(options, previousReplayId) ?: return@submitSafely
            val segment = createSegment(
                duration = lastSegment.duration,
                currentSegmentTimestamp = lastSegment.timestamp,
                replayId = previousReplayId,
                segmentId = lastSegment.id,
                height = lastSegment.recorderConfig.recordingHeight,
                width = lastSegment.recorderConfig.recordingWidth,
                frameRate = lastSegment.recorderConfig.frameRate,
                cache = lastSegment.cache,
                replayType = lastSegment.replayType,
                screenAtStart = lastSegment.screenAtStart,
                breadcrumbs = breadcrumbs,
                events = LinkedList(lastSegment.events)
            )

            if (segment is ReplaySegment.Created) {
                segment.capture(hub)
            }
            FileUtils.deleteRecursively(lastSegment.cache.replayCacheDir)
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

    private class ReplayPersistingExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayPersister-" + cnt++)
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

    private inline fun <T> persistableAtomicNullable(
        initialValue: T? = null,
        propertyName: String? = null,
        crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit = { _, _, newValue ->
            propertyName ?: error("Can't persist value without a property name")

            if (options.mainThreadChecker.isMainThread) {
                persistingExecutor.submit {
                    cache?.persistSegmentValues(propertyName, newValue.toString())
                }
            } else {
                cache?.persistSegmentValues(propertyName, newValue.toString())
            }
        }
    ): ReadWriteProperty<Any?, T?> =
        object : ReadWriteProperty<Any?, T?> {
            private val value = AtomicReference(initialValue)

            init {
                onChange(propertyName, initialValue, initialValue)
            }

            override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value.get()

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
                val oldValue = this.value.getAndSet(value)
                if (oldValue != value) {
                    onChange(propertyName, oldValue, value)
                }
            }
        }

    private inline fun <T> persistableAtomic(
        initialValue: T? = null,
        propertyName: String? = null,
        crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit = { _, _, newValue ->
            propertyName ?: error("Can't persist value without a property name")

            if (options.mainThreadChecker.isMainThread) {
                persistingExecutor.submit {
                    cache?.persistSegmentValues(propertyName, newValue.toString())
                }
            } else {
                cache?.persistSegmentValues(propertyName, newValue.toString())
            }
        }
    ): ReadWriteProperty<Any?, T> =
        persistableAtomicNullable<T>(initialValue, propertyName, onChange) as ReadWriteProperty<Any?, T>

    private class PersistableLinkedList(
        private val propertyName: String,
        private val options: SentryOptions,
        private val persistingExecutor: ScheduledExecutorService,
        private val cacheProvider: () -> ReplayCache?
    ) : LinkedList<RRWebEvent>() {
        // only overriding methods that we use, to observe the collection
        override fun addAll(elements: Collection<RRWebEvent>): Boolean {
            val result = super.addAll(elements)
            persistRecording()
            return result
        }

        override fun add(element: RRWebEvent): Boolean {
            val result = super.add(element)
            persistRecording()
            return result
        }

        override fun remove(): RRWebEvent {
            val result = super.remove()
            persistRecording()
            return result
        }

        private fun persistRecording() {
            val cache = cacheProvider() ?: return
            val recording = ReplayRecording().apply { payload = this@PersistableLinkedList }
            if (options.mainThreadChecker.isMainThread) {
                persistingExecutor.submit {
                    val stringWriter = StringWriter()
                    options.serializer.serialize(recording, BufferedWriter(stringWriter))
                    cache.persistSegmentValues(propertyName, stringWriter.toString())
                }
            } else {
                val stringWriter = StringWriter()
                options.serializer.serialize(recording, BufferedWriter(stringWriter))
                cache.persistSegmentValues(propertyName, stringWriter.toString())
            }
        }
    }
}
