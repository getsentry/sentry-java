package io.sentry.android.replay

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DataCategory.All
import io.sentry.DataCategory.Replay
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
import io.sentry.IConnectionStatusProvider.IConnectionStatusObserver
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.NoOpReplayBreadcrumbConverter
import io.sentry.ReplayBreadcrumbConverter
import io.sentry.ReplayController
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplayState.CLOSED
import io.sentry.android.replay.ReplayState.PAUSED
import io.sentry.android.replay.ReplayState.RESUMED
import io.sentry.android.replay.ReplayState.STARTED
import io.sentry.android.replay.ReplayState.STOPPED
import io.sentry.android.replay.capture.BufferCaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.gestures.GestureRecorder
import io.sentry.android.replay.gestures.TouchRecorderCallback
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.appContext
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
import io.sentry.hints.Backfillable
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import io.sentry.transport.RateLimiter
import io.sentry.transport.RateLimiter.IRateLimitObserver
import io.sentry.util.AutoClosableReentrantLock
import io.sentry.util.FileUtils
import io.sentry.util.HintUtils
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Random
import java.io.Closeable
import java.io.File
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

public class ReplayIntegration(
  private val context: Context,
  private val dateProvider: ICurrentDateProvider,
  private val recorderProvider: (() -> Recorder)? = null,
  private val replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
) :
  Integration,
  Closeable,
  ScreenshotRecorderCallback,
  TouchRecorderCallback,
  ReplayController,
  IConnectionStatusObserver,
  IRateLimitObserver,
  WindowCallback {
  private companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-replay", BuildConfig.VERSION_NAME)
    }
  }

  // needed for the Java's call site
  public constructor(
    context: Context,
    dateProvider: ICurrentDateProvider,
  ) : this(context.appContext(), dateProvider, null, null)

  internal constructor(
    context: Context,
    dateProvider: ICurrentDateProvider,
    recorderProvider: (() -> Recorder)?,
    replayCacheProvider: ((replayId: SentryId) -> ReplayCache)?,
    replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null,
    mainLooperHandler: MainLooperHandler? = null,
    gestureRecorderProvider: (() -> GestureRecorder)? = null,
  ) : this(context.appContext(), dateProvider, recorderProvider, replayCacheProvider) {
    this.replayCaptureStrategyProvider = replayCaptureStrategyProvider
    this.mainLooperHandler = mainLooperHandler ?: MainLooperHandler()
    this.gestureRecorderProvider = gestureRecorderProvider
  }

  private var debugMaskingEnabled: Boolean = false
  private lateinit var options: SentryOptions
  private var scopes: IScopes? = null
  private var recorder: Recorder? = null
  private var gestureRecorder: GestureRecorder? = null
  private val random by lazy { Random() }
  internal val rootViewsSpy by lazy { RootViewsSpy.install() }
  private val replayExecutor by lazy {
    Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
  }

  internal val isEnabled = AtomicBoolean(false)
  internal val isManualPause = AtomicBoolean(false)
  private var captureStrategy: CaptureStrategy? = null
  public val replayCacheDir: File?
    get() = captureStrategy?.replayCacheDir

  private var replayBreadcrumbConverter: ReplayBreadcrumbConverter =
    NoOpReplayBreadcrumbConverter.getInstance()
  private var replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null
  private var mainLooperHandler: MainLooperHandler = MainLooperHandler()
  private var gestureRecorderProvider: (() -> GestureRecorder)? = null
  private val lifecycleLock = AutoClosableReentrantLock()
  private val lifecycle = ReplayLifecycle()

  override fun register(scopes: IScopes, options: SentryOptions) {
    this.options = options

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      options.logger.log(INFO, "Session replay is only supported on API 26 and above")
      return
    }

    if (
      !options.sessionReplay.isSessionReplayEnabled &&
        !options.sessionReplay.isSessionReplayForErrorsEnabled
    ) {
      options.logger.log(INFO, "Session replay is disabled, no sample rate specified")
      return
    }

    this.scopes = scopes
    recorder =
      recorderProvider?.invoke()
        ?: WindowRecorder(options, this, this, mainLooperHandler, replayExecutor)
    gestureRecorder = gestureRecorderProvider?.invoke() ?: GestureRecorder(options, this)
    isEnabled.set(true)

    options.connectionStatusProvider.addConnectionStatusObserver(this)
    scopes.rateLimiter?.addRateLimitObserver(this)

    addIntegrationToSdkVersion("Replay")

    finalizePreviousReplay()
  }

  override fun isRecording(): Boolean =
    lifecycle.currentState >= STARTED && lifecycle.currentState < STOPPED

  override fun start() {
    lifecycleLock.acquire().use {
      if (!isEnabled.get()) {
        return
      }

      if (!lifecycle.isAllowed(STARTED)) {
        options.logger.log(
          DEBUG,
          "Session replay is already being recorded, not starting a new one",
        )
        return
      }

      val isFullSession = random.sample(options.sessionReplay.sessionSampleRate)
      if (!isFullSession && !options.sessionReplay.isSessionReplayForErrorsEnabled) {
        options.logger.log(
          INFO,
          "Session replay is not started, full session was not sampled and onErrorSampleRate is not specified",
        )
        return
      }

      lifecycle.currentState = STARTED
      captureStrategy =
        replayCaptureStrategyProvider?.invoke(isFullSession)
          ?: if (isFullSession) {
            SessionCaptureStrategy(
              options,
              scopes,
              dateProvider,
              replayExecutor,
              replayCacheProvider,
            )
          } else {
            BufferCaptureStrategy(
              options,
              scopes,
              dateProvider,
              random,
              replayExecutor,
              replayCacheProvider,
            )
          }
      recorder?.start()
      captureStrategy?.start()

      registerRootViewListeners()
    }
  }

  override fun resume() {
    isManualPause.set(false)
    resumeInternal()
  }

  private fun resumeInternal() {
    lifecycleLock.acquire().use {
      if (!isEnabled.get() || !lifecycle.isAllowed(RESUMED)) {
        return
      }

      if (
        isManualPause.get() ||
          options.connectionStatusProvider.connectionStatus == DISCONNECTED ||
          scopes?.rateLimiter?.isActiveForCategory(All) == true ||
          scopes?.rateLimiter?.isActiveForCategory(Replay) == true
      ) {
        return
      }

      lifecycle.currentState = RESUMED
      captureStrategy?.resume()
      recorder?.resume()
    }
  }

  override fun captureReplay(isTerminating: Boolean?) {
    if (!isEnabled.get() || !isRecording()) {
      return
    }

    if (SentryId.EMPTY_ID.equals(captureStrategy?.currentReplayId)) {
      options.logger.log(DEBUG, "Replay id is not set, not capturing for event")
      return
    }

    captureStrategy?.captureReplay(
      isTerminating == true,
      onSegmentSent = { newTimestamp ->
        captureStrategy?.currentSegment = captureStrategy?.currentSegment!! + 1
        captureStrategy?.segmentTimestamp = newTimestamp
      },
    )
    captureStrategy = captureStrategy?.convert()
  }

  override fun getReplayId(): SentryId = captureStrategy?.currentReplayId ?: SentryId.EMPTY_ID

  override fun setBreadcrumbConverter(converter: ReplayBreadcrumbConverter) {
    replayBreadcrumbConverter = converter
  }

  override fun getBreadcrumbConverter(): ReplayBreadcrumbConverter = replayBreadcrumbConverter

  override fun pause() {
    isManualPause.set(true)
    pauseInternal()
  }

  override fun enableDebugMaskingOverlay() {
    debugMaskingEnabled = true
  }

  override fun disableDebugMaskingOverlay() {
    debugMaskingEnabled = false
  }

  override fun isDebugMaskingOverlayEnabled(): Boolean = debugMaskingEnabled

  private fun pauseInternal() {
    lifecycleLock.acquire().use {
      if (!isEnabled.get() || !lifecycle.isAllowed(PAUSED)) {
        return
      }

      recorder?.pause()
      captureStrategy?.pause()
      lifecycle.currentState = PAUSED
    }
  }

  override fun stop() {
    lifecycleLock.acquire().use {
      if (!isEnabled.get() || !lifecycle.isAllowed(STOPPED)) {
        return
      }

      unregisterRootViewListeners()
      recorder?.reset()
      recorder?.stop()
      gestureRecorder?.stop()
      captureStrategy?.stop()
      captureStrategy = null
      lifecycle.currentState = STOPPED
    }
  }

  override fun onScreenshotRecorded(bitmap: Bitmap) {
    var screen: String? = null
    scopes?.configureScope { screen = it.screen?.substringAfterLast('.') }
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
    lifecycleLock.acquire().use {
      if (!isEnabled.get() || !lifecycle.isAllowed(CLOSED)) {
        return
      }

      options.connectionStatusProvider.removeConnectionStatusObserver(this)
      scopes?.rateLimiter?.removeRateLimitObserver(this)
      stop()
      recorder?.close()
      recorder = null
      rootViewsSpy.close()
      replayExecutor.gracefullyShutdown(options)
      lifecycle.currentState = CLOSED
    }
  }

  override fun onConnectionStatusChanged(status: ConnectionStatus) {
    if (captureStrategy !is SessionCaptureStrategy) {
      // we only want to stop recording when offline for session mode
      return
    }

    if (status == DISCONNECTED) {
      pauseInternal()
    } else {
      // being positive for other states, even if it's NO_PERMISSION
      resumeInternal()
    }
  }

  override fun onRateLimitChanged(rateLimiter: RateLimiter) {
    if (captureStrategy !is SessionCaptureStrategy) {
      // we only want to stop recording when rate-limited for session mode
      return
    }

    if (rateLimiter.isActiveForCategory(All) || rateLimiter.isActiveForCategory(Replay)) {
      pauseInternal()
    } else {
      resumeInternal()
    }
  }

  override fun onTouchEvent(event: MotionEvent) {
    if (!isEnabled.get() || !lifecycle.isTouchRecordingAllowed()) {
      return
    }
    captureStrategy?.onTouchEvent(event)
  }

  /**
   * Check if we're offline or rate-limited and pause for session mode to not overflow the envelope
   * cache.
   */
  private fun checkCanRecord() {
    if (
      captureStrategy is SessionCaptureStrategy &&
        (options.connectionStatusProvider.connectionStatus == DISCONNECTED ||
          scopes?.rateLimiter?.isActiveForCategory(All) == true ||
          scopes?.rateLimiter?.isActiveForCategory(Replay) == true)
    ) {
      pauseInternal()
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
        if (
          name.startsWith("replay_") &&
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
    // TODO: previous run and set them directly to the ReplayEvent so they don't get overwritten in
    // MainEventProcessor

    options.executorService.submitSafely(options, "ReplayIntegration.finalize_previous_replay") {
      val persistingScopeObserver = options.findPersistingScopeObserver()
      val previousReplayIdString =
        persistingScopeObserver?.read(options, REPLAY_FILENAME, String::class.java)
          ?: run {
            cleanupReplays()
            return@submitSafely
          }
      val previousReplayId = SentryId(previousReplayIdString)
      if (previousReplayId == SentryId.EMPTY_ID) {
        cleanupReplays()
        return@submitSafely
      }
      val lastSegment =
        ReplayCache.fromDisk(options, previousReplayId, replayCacheProvider)
          ?: run {
            cleanupReplays()
            return@submitSafely
          }

      @Suppress("UNCHECKED_CAST")
      val breadcrumbs =
        persistingScopeObserver.read(options, BREADCRUMBS_FILENAME, List::class.java)
          as? List<Breadcrumb>
      val segment =
        CaptureStrategy.createSegment(
          scopes = scopes,
          options = options,
          duration = lastSegment.duration,
          currentSegmentTimestamp = lastSegment.timestamp,
          replayId = previousReplayId,
          segmentId = lastSegment.id,
          height = lastSegment.recorderConfig.recordingHeight,
          width = lastSegment.recorderConfig.recordingWidth,
          frameRate = lastSegment.recorderConfig.frameRate,
          bitRate = lastSegment.recorderConfig.bitRate,
          cache = lastSegment.cache,
          replayType = lastSegment.replayType,
          screenAtStart = lastSegment.screenAtStart,
          breadcrumbs = breadcrumbs,
          events = LinkedList(lastSegment.events),
        )

      if (segment is ReplaySegment.Created) {
        val hint = HintUtils.createWithTypeCheckHint(PreviousReplayHint())
        segment.capture(scopes, hint)
      }
      cleanupReplays(
        unfinishedReplayId = previousReplayIdString
      ) // will be cleaned up after the envelope is assembled
    }
  }

  override fun onWindowSizeChanged(width: Int, height: Int) {
    if (!isEnabled.get() || !isRecording()) {
      return
    }
    if (options.sessionReplay.isTrackConfiguration) {
      val recorderConfig =
        ScreenshotRecorderConfig.fromSize(context, options.sessionReplay, width, height)
      onConfigurationChanged(recorderConfig)
    }
  }

  public fun onConfigurationChanged(config: ScreenshotRecorderConfig) {
    if (!isEnabled.get() || !isRecording()) {
      return
    }
    captureStrategy?.onConfigurationChanged(config)
    recorder?.onConfigurationChanged(config)

    // we have to restart recorder with a new config and pause immediately if the replay is paused
    if (lifecycle.currentState == PAUSED) {
      recorder?.pause()
    }
  }

  private class PreviousReplayHint : Backfillable {
    override fun shouldEnrich(): Boolean = false
  }

  private class ReplayExecutorServiceThreadFactory : ThreadFactory {
    private var cnt = 0

    override fun newThread(r: Runnable): Thread {
      val ret = Thread(r, "SentryReplayIntegration-" + cnt++)
      ret.setDaemon(true)
      return ret
    }
  }
}
