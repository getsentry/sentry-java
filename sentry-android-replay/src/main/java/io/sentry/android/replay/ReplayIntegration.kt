package io.sentry.android.replay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.Integration
import io.sentry.NoOpReplayBreadcrumbConverter
import io.sentry.ReplayBreadcrumbConverter
import io.sentry.ReplayController
import io.sentry.ScopeObserverAdapter
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.capture.BufferCaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.sample
import io.sentry.protocol.Contexts
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.io.Closeable
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

public class ReplayIntegration(
    private val context: Context,
    private val dateProvider: ICurrentDateProvider,
    private val recorderProvider: (() -> Recorder)? = null,
    private val recorderConfigProvider: ((configChanged: Boolean) -> ScreenshotRecorderConfig)? = null,
    private val replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : Integration, Closeable, ScreenshotRecorderCallback, TouchRecorderCallback, ReplayController, ComponentCallbacks {

    // needed for the Java's call site
    constructor(context: Context, dateProvider: ICurrentDateProvider) : this(
        context,
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
        mainLooperHandler: MainLooperHandler? = null
    ) : this(context, dateProvider, recorderProvider, recorderConfigProvider, replayCacheProvider) {
        this.replayCaptureStrategyProvider = replayCaptureStrategyProvider
        this.mainLooperHandler = mainLooperHandler ?: MainLooperHandler()
    }

    private lateinit var options: SentryOptions
    private var hub: IHub? = null
    private var recorder: Recorder? = null
    private val random by lazy { SecureRandom() }

    // TODO: probably not everything has to be thread-safe here
    internal val isEnabled = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private var captureStrategy: CaptureStrategy? = null
    public val replayCacheDir: File? get() = captureStrategy?.replayCacheDir
    private var replayBreadcrumbConverter: ReplayBreadcrumbConverter = NoOpReplayBreadcrumbConverter.getInstance()
    private var replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null
    private var mainLooperHandler: MainLooperHandler = MainLooperHandler()

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
        this.options.addScopeObserver(object : ScopeObserverAdapter() {
            override fun setContexts(contexts: Contexts) {
                // scope screen has fully-qualified name
                captureStrategy?.onScreenChanged(contexts.app?.viewNames?.lastOrNull()?.substringAfterLast('.'))
            }
        })
        recorder = recorderProvider?.invoke() ?: WindowRecorder(options, this, this, mainLooperHandler)
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

        val isFullSession = random.sample(options.experimental.sessionReplay.sessionSampleRate)
        if (!isFullSession && !options.experimental.sessionReplay.isSessionReplayForErrorsEnabled) {
            options.logger.log(INFO, "Session replay is not started, full session was not sampled and errorSampleRate is not specified")
            return
        }

        recorderConfig = recorderConfigProvider?.invoke(false) ?: ScreenshotRecorderConfig.from(context, options.experimental.sessionReplay)
        captureStrategy = replayCaptureStrategyProvider?.invoke(isFullSession) ?: if (isFullSession) {
            SessionCaptureStrategy(options, hub, dateProvider, recorderConfig, replayCacheProvider = replayCacheProvider)
        } else {
            BufferCaptureStrategy(options, hub, dateProvider, recorderConfig, random, replayCacheProvider)
        }

        captureStrategy?.start()
        recorder?.start(recorderConfig)
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

        sendReplay(event.isCrashed, event.eventId.toString(), hint)
    }

    override fun sendReplay(isCrashed: Boolean?, eventId: String?, hint: Hint?) {
        if (!isEnabled.get() || !isRecording.get()) {
            return
        }

        if (SentryId.EMPTY_ID.equals(captureStrategy?.currentReplayId?.get())) {
            options.logger.log(DEBUG, "Replay id is not set, not capturing for event %s", eventId)
            return
        }

        captureStrategy?.sendReplayForEvent(isCrashed == true, eventId, hint, onSegmentSent = { captureStrategy?.currentSegment?.getAndIncrement() })
        captureStrategy = captureStrategy?.convert()
    }

    override fun getReplayId(): SentryId = captureStrategy?.currentReplayId?.get() ?: SentryId.EMPTY_ID

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

        recorder?.stop()
        captureStrategy?.stop()
        isRecording.set(false)
        captureStrategy?.close()
        captureStrategy = null
    }

    override fun onScreenshotRecorded(bitmap: Bitmap) {
        captureStrategy?.onScreenshotRecorded { frameTimeStamp ->
            addFrame(bitmap, frameTimeStamp)
        }
    }

    override fun onScreenshotRecorded(screenshot: File, frameTimestamp: Long) {
        captureStrategy?.onScreenshotRecorded { _ ->
            addFrame(screenshot, frameTimestamp)
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

    override fun onLowMemory() = Unit

    override fun onTouchEvent(event: MotionEvent) {
        captureStrategy?.onTouchEvent(event)
    }
}
