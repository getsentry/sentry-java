package io.sentry.android.replay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DataCategory.All
import io.sentry.DataCategory.Replay
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
import io.sentry.IConnectionStatusProvider.IConnectionStatusObserver
import io.sentry.IHub
import io.sentry.Integration
import io.sentry.NoOpReplayBreadcrumbConverter
import io.sentry.ReplayBreadcrumbConverter
import io.sentry.ReplayController
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.capture.BufferCaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.gestures.GestureRecorder
import io.sentry.android.replay.gestures.TouchRecorderCallback
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.appContext
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.cache.PersistingScopeObserver
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
import io.sentry.hints.Backfillable
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.transport.RateLimiter
import io.sentry.transport.RateLimiter.IRateLimitObserver
import io.sentry.util.FileUtils
import io.sentry.util.HintUtils
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Random
import java.io.Closeable
import java.io.File
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

public class ReplayIntegration(
    private val context: Context,
    private val dateProvider: ICurrentDateProvider,
    private val recorderProvider: (() -> Recorder)? = null,
    private val recorderConfigProvider: ((configChanged: Boolean) -> ScreenshotRecorderConfig)? = null,
    private val replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : Integration,
    Closeable,
    ScreenshotRecorderCallback,
    TouchRecorderCallback,
    ReplayController,
    ComponentCallbacks,
    IConnectionStatusObserver,
    IRateLimitObserver {

    // needed for the Java's call site
    constructor(context: Context, dateProvider: ICurrentDateProvider) : this(
        context.appContext(),
        dateProvider,
        null,
        null,
        null
    )

    internal constructor(
        context: Context,
        dateProvider: ICurrentDateProvider,
        recorderProvider: (() -> Recorder)?,
        recorderConfigProvider: ((configChanged: Boolean) -> ScreenshotRecorderConfig)?,
        replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)?,
        replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null,
        mainLooperHandler: MainLooperHandler? = null,
        gestureRecorderProvider: (() -> GestureRecorder)? = null
    ) : this(context.appContext(), dateProvider, recorderProvider, recorderConfigProvider, replayCacheProvider) {
        this.replayCaptureStrategyProvider = replayCaptureStrategyProvider
        this.mainLooperHandler = mainLooperHandler ?: MainLooperHandler()
        this.gestureRecorderProvider = gestureRecorderProvider
    }

    private lateinit var options: SentryOptions
    private var hub: IHub? = null
    private var recorder: Recorder? = null
    private var gestureRecorder: GestureRecorder? = null
    private val random by lazy { Random() }
    private val rootViewsSpy by lazy(NONE) { RootViewsSpy.install() }

    // TODO: probably not everything has to be thread-safe here
    internal val isEnabled = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private var captureStrategy: CaptureStrategy? = null
    public val replayCacheDir: File? get() = captureStrategy?.replayCacheDir
    private var replayBreadcrumbConverter: ReplayBreadcrumbConverter = NoOpReplayBreadcrumbConverter.getInstance()
    private var replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null
    private var mainLooperHandler: MainLooperHandler = MainLooperHandler()
    private var gestureRecorderProvider: (() -> GestureRecorder)? = null

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

        this.hub = hub
        recorder = recorderProvider?.invoke() ?: WindowRecorder(options, this, mainLooperHandler)
        gestureRecorder = gestureRecorderProvider?.invoke() ?: GestureRecorder(options, this)
        isEnabled.set(true)

        options.connectionStatusProvider.addConnectionStatusObserver(this)
        hub.rateLimiter?.addRateLimitObserver(this)
        try {
            context.registerComponentCallbacks(this)
        } catch (e: Throwable) {
            options.logger.log(INFO, "ComponentCallbacks is not available, orientation changes won't be handled by Session replay", e)
        }

        addIntegrationToSdkVersion("Replay")
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-android-replay", BuildConfig.VERSION_NAME)

        finalizePreviousReplay()
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

        val isFullSession = random.sample(options.experimental.sessionReplay.sessionSampleRate)
        if (!isFullSession && !options.experimental.sessionReplay.isSessionReplayForErrorsEnabled) {
            options.logger.log(INFO, "Session replay is not started, full session was not sampled and onErrorSampleRate is not specified")
            return
        }

        recorderConfig = recorderConfigProvider?.invoke(false) ?: ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        captureStrategy = replayCaptureStrategyProvider?.invoke(isFullSession) ?: if (isFullSession) {
            SessionCaptureStrategy(options, hub, dateProvider, replayCacheProvider = replayCacheProvider)
        } else {
            BufferCaptureStrategy(options, hub, dateProvider, random, replayCacheProvider = replayCacheProvider)
        }

        captureStrategy?.start(recorderConfig)
        recorder?.start(recorderConfig)
        registerRootViewListeners()
    }

    override fun resume() {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        captureStrategy?.resume()
        recorder?.resume()
    }

    override fun captureReplay(isTerminating: Boolean?) {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        if (SentryId.EMPTY_ID.equals(captureStrategy?.currentReplayId)) {
            options.logger.log(DEBUG, "Replay id is not set, not capturing for event")
            return
        }

        captureStrategy?.captureReplay(isTerminating == true, onSegmentSent = { newTimestamp ->
            captureStrategy?.currentSegment = captureStrategy?.currentSegment!! + 1
            captureStrategy?.segmentTimestamp = newTimestamp
        })
        captureStrategy = captureStrategy?.convert()
    }

    override fun getReplayId(): SentryId = captureStrategy?.currentReplayId ?: SentryId.EMPTY_ID

    override fun setBreadcrumbConverter(converter: ReplayBreadcrumbConverter) {
        replayBreadcrumbConverter = converter
    }

    override fun getBreadcrumbConverter(): ReplayBreadcrumbConverter = replayBreadcrumbConverter

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

        unregisterRootViewListeners()
        recorder?.stop()
        gestureRecorder?.stop()
        captureStrategy?.stop()
        isRecording.set(false)
        captureStrategy?.close()
        captureStrategy = null
    }

    override fun onScreenshotRecorded(bitmap: Bitmap) {
        var screen: String? = null
        hub?.configureScope { screen = it.screen?.substringAfterLast('.') }
        captureStrategy?.onScreenshotRecorded(bitmap) { frameTimeStamp ->
            addFrame(bitmap, frameTimeStamp, screen)
            checkCanRecord()
        }
    }

    override fun onScreenshotRecorded(screenshot: File, frameTimestamp: Long) {
        captureStrategy?.onScreenshotRecorded { _ ->
            addFrame(screenshot, frameTimestamp)
            checkCanRecord()
        }
    }

    override fun close() {
        if (!isEnabled.get()) {
            return
        }

        options.connectionStatusProvider.removeConnectionStatusObserver(this)
        hub?.rateLimiter?.removeRateLimitObserver(this)
        try {
            context.unregisterComponentCallbacks(this)
        } catch (ignored: Throwable) {
        }
        stop()
        recorder?.close()
        recorder = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        recorder?.stop()

        // refresh config based on new device configuration
        recorderConfig = recorderConfigProvider?.invoke(true) ?: ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        captureStrategy?.onConfigurationChanged(recorderConfig)

        recorder?.start(recorderConfig)
    }

    override fun onConnectionStatusChanged(status: ConnectionStatus) {
        if (captureStrategy !is SessionCaptureStrategy) {
            // we only want to stop recording when offline for session mode
            return
        }

        if (status == DISCONNECTED) {
            pause()
        } else {
            // being positive for other states, even if it's NO_PERMISSION
            resume()
        }
    }

    override fun onRateLimitChanged(rateLimiter: RateLimiter) {
        if (captureStrategy !is SessionCaptureStrategy) {
            // we only want to stop recording when rate-limited for session mode
            return
        }

        if (rateLimiter.isActiveForCategory(All) || rateLimiter.isActiveForCategory(Replay)) {
            pause()
        } else {
            resume()
        }
    }

    override fun onLowMemory() = Unit

    override fun onTouchEvent(event: MotionEvent) {
        captureStrategy?.onTouchEvent(event)
    }

    /**
     * Check if we're offline or rate-limited and pause for session mode to not overflow the
     * envelope cache.
     */
    private fun checkCanRecord() {
        if (captureStrategy is SessionCaptureStrategy &&
            (
                options.connectionStatusProvider.connectionStatus == DISCONNECTED ||
                    hub?.rateLimiter?.isActiveForCategory(All) == true ||
                    hub?.rateLimiter?.isActiveForCategory(Replay) == true
                )
        ) {
            pause()
        }
    }

    private fun registerRootViewListeners() {
        if (recorder is OnRootViewsChangedListener) {
            rootViewsSpy.listeners += (recorder as OnRootViewsChangedListener)
        }
        rootViewsSpy.listeners += gestureRecorder
    }

    private fun unregisterRootViewListeners() {
        if (recorder is OnRootViewsChangedListener) {
            rootViewsSpy.listeners -= (recorder as OnRootViewsChangedListener)
        }
        rootViewsSpy.listeners -= gestureRecorder
    }

    private fun cleanupReplays(unfinishedReplayId: String = "") {
        // clean up old replays
        options.cacheDirPath?.let { cacheDir ->
            File(cacheDir).listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("replay_") &&
                    !name.contains(replayId.toString()) &&
                    !(unfinishedReplayId.isNotBlank() && name.contains(unfinishedReplayId))
                ) {
                    FileUtils.deleteRecursively(file)
                }
            }
        }
    }

    private fun finalizePreviousReplay() {
        // TODO: read persisted options/scope values form the
        // TODO: previous run and set them directly to the ReplayEvent so they don't get overwritten in MainEventProcessor

        options.executorService.submitSafely(options, "ReplayIntegration.finalize_previous_replay") {
            val previousReplayIdString = PersistingScopeObserver.read(options, REPLAY_FILENAME, String::class.java) ?: run {
                cleanupReplays()
                return@submitSafely
            }
            val previousReplayId = SentryId(previousReplayIdString)
            if (previousReplayId == SentryId.EMPTY_ID) {
                cleanupReplays()
                return@submitSafely
            }
            val lastSegment = ReplayCache.fromDisk(options, previousReplayId, replayCacheProvider) ?: run {
                cleanupReplays()
                return@submitSafely
            }
            val breadcrumbs = PersistingScopeObserver.read(options, BREADCRUMBS_FILENAME, List::class.java, Breadcrumb.Deserializer()) as? List<Breadcrumb>
            val segment = CaptureStrategy.createSegment(
                hub = hub,
                options = options,
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
                val hint = HintUtils.createWithTypeCheckHint(PreviousReplayHint())
                segment.capture(hub, hint)
            }
            cleanupReplays(unfinishedReplayId = previousReplayIdString) // will be cleaned up after the envelope is assembled
        }
    }

    private class PreviousReplayHint : Backfillable {
        override fun shouldEnrich(): Boolean = false
    }
}
