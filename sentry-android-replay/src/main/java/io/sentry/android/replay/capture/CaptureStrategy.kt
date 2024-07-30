package io.sentry.android.replay.capture

import android.graphics.Bitmap
import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ReplayRecording
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import java.io.File
import java.util.Date
import java.util.LinkedList

internal interface CaptureStrategy {
    var currentSegment: Int
    var currentReplayId: SentryId
    val replayCacheDir: File?

    fun start(
        recorderConfig: ScreenshotRecorderConfig,
        segmentId: Int = 0,
        replayId: SentryId = SentryId()
    )

    fun stop()

    fun pause()

    fun resume()

    fun captureReplay(isTerminating: Boolean, onSegmentSent: () -> Unit)

    fun onScreenshotRecorded(bitmap: Bitmap? = null, store: ReplayCache.(frameTimestamp: Long) -> Unit)

    fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig)

    fun onTouchEvent(event: MotionEvent)

    fun onScreenChanged(screen: String?) = Unit

    fun convert(): CaptureStrategy

    fun close()

    companion object {
        internal val currentEventsLock = Any()

        fun createSegment(
            hub: IHub?,
            options: SentryOptions,
            duration: Long,
            currentSegmentTimestamp: Date,
            replayId: SentryId,
            segmentId: Int,
            height: Int,
            width: Int,
            replayType: ReplayType,
            cache: ReplayCache?,
            frameRate: Int,
            screenAtStart: String?,
            breadcrumbs: List<Breadcrumb>?,
            events: LinkedList<RRWebEvent>
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
                options,
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
            options: SentryOptions,
            video: File,
            currentReplayId: SentryId,
            segmentTimestamp: Date,
            segmentId: Int,
            height: Int,
            width: Int,
            frameCount: Int,
            frameRate: Int,
            videoDuration: Long,
            replayType: ReplayType,
            screenAtStart: String?,
            breadcrumbs: List<Breadcrumb>,
            events: LinkedList<RRWebEvent>
        ): ReplaySegment {
            val endTimestamp = DateUtils.getDateTime(segmentTimestamp.time + videoDuration)
            val replay = SentryReplayEvent().apply {
                this.eventId = currentReplayId
                this.replayId = currentReplayId
                this.segmentId = segmentId
                this.timestamp = endTimestamp
                this.replayStartTimestamp = segmentTimestamp
                this.replayType = replayType
                this.videoFile = video
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
                this.durationMs = videoDuration
                this.frameCount = frameCount
                this.size = video.length()
                this.frameRate = frameRate
                this.height = height
                this.width = width
                // TODO: support non-fullscreen windows later
                this.left = 0
                this.top = 0
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

            if (screenAtStart != null && urls.firstOrNull() != screenAtStart) {
                urls.addFirst(screenAtStart)
            }

            rotateEvents(events, endTimestamp.time) { event ->
                if (event.timestamp >= segmentTimestamp.time) {
                    recordingPayload += event
                }
            }

            val recording = ReplayRecording().apply {
                this.segmentId = segmentId
                this.payload = recordingPayload.sortedBy { it.timestamp }
            }

            replay.urls = urls
            return ReplaySegment.Created(
                videoDuration = videoDuration,
                replay = replay,
                recording = recording
            )
        }

        internal fun rotateEvents(
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
    }

    sealed class ReplaySegment {
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
}
