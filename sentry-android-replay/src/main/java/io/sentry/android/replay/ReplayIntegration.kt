package io.sentry.android.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DataCategory.All
import io.sentry.DataCategory.Replay
import io.sentry.Hint
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
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.TypeCheckHint
import io.sentry.android.replay.ReplayLifecycleState.CLOSED
import io.sentry.android.replay.ReplayLifecycleState.PAUSED
import io.sentry.android.replay.ReplayLifecycleState.RESUMED
import io.sentry.android.replay.ReplayLifecycleState.STARTED
import io.sentry.android.replay.ReplayLifecycleState.STOPPED
import io.sentry.android.replay.capture.BufferCaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.gestures.GestureRecorder
import io.sentry.android.replay.gestures.TouchRecorderCallback
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.ReplayExecutorService
import io.sentry.android.replay.util.appContext
import io.sentry.android.replay.util.sample
import io.sentry.android.replay.util.submitSafely
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

  private var lastKnownConnectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN
  private var debugMaskingEnabled: Boolean = false
  private lateinit var options: SentryOptions
  private var scopes: IScopes? = null
  private var recorder: Recorder? = null
  private var gestureRecorder: GestureRecorder? = null
  private val random = ThreadLocal<Random>()
  internal val rootViewsSpy by lazy { RootViewsSpy.install() }
  internal val lazyReplayExecutor = lazy {
    val delegate = Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
    ReplayExecutorService(delegate, options)
  }
  internal val replayExecutor by lazyReplayExecutor
  internal val lazyPersistingExecutor = lazy {
    val delegate =
      Executors.newSingleThreadScheduledExecutor(ReplayPersistingExecutorServiceThreadFactory())
    ReplayExecutorService(delegate, options)
  }
  internal val persistingExecutor by lazyPersistingExecutor

  internal val isEnabled = AtomicBoolean(false)
  internal var isManualPause = false
  public val replayCacheDir: File?
    get() = state.get().captureStrategy?.replayCacheDir

  private var replayBreadcrumbConverter: ReplayBreadcrumbConverter =
    NoOpReplayBreadcrumbConverter.getInstance()
  private var replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null
  private var mainLooperHandler: MainLooperHandler = MainLooperHandler()
  private var gestureRecorderProvider: (() -> GestureRecorder)? = null
  private val state = AtomicReference(ReplayState())

  override fun register(scopes: IScopes, options: SentryOptions) {
    this.options = options

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      options.logger.log(INFO, "Session replay is only supported on API 26 and above")
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

  override fun isRecording(): Boolean = state.get().isRecording

  override fun start() {
    enqueueOnMainThread { startInternal(isFullSession = true) }
  }

  override fun startBuffering() {
    enqueueOnMainThread { startInternal(isFullSession = false) }
  }

  override fun onAppForegrounded(startNewSession: Boolean) {
    if (!isEnabled.get()) {
      return
    }
    enqueueOnMainThread {
      if (startNewSession) {
        val isFullSession = sample(options.sessionReplay.sessionSampleRate)
        if (!isFullSession && !options.sessionReplay.isSessionReplayForErrorsEnabled) {
          options.logger.log(
            INFO,
            "Session replay is not started, full session was not sampled and onErrorSampleRate is not specified",
          )
        } else {
          startInternal(isFullSession)
        }
      }
      resumeInternal()
    }
  }

  override fun onAppBackgrounded() {
    if (!isEnabled.get()) {
      return
    }
    enqueueOnMainThread { pauseInternal() }
  }

  private fun startInternal(isFullSession: Boolean) {
    if (!isEnabled.get()) {
      return
    }

    val current = state.get()
    if (!current.lifecycleState.isAllowed(STARTED)) {
      options.logger.log(
        DEBUG,
        "Session replay is already being recorded, not starting a new one",
      )
      return
    }

    isManualPause = false
    val strategy =
      replayCaptureStrategyProvider?.invoke(isFullSession)
        ?: if (isFullSession) {
          SessionCaptureStrategy(
            options,
            scopes,
            dateProvider,
            replayExecutor,
            persistingExecutor,
            replayCacheProvider,
          )
        } else {
          BufferCaptureStrategy(
            options,
            scopes,
            dateProvider,
            replayExecutor,
            persistingExecutor,
            replayCacheProvider,
          )
        }
    recorder?.start()
    strategy.start()
    val replayId: SentryId? = strategy.currentReplayId
    state.set(
      ReplayState(
        generation = current.generation + 1,
        lifecycleState = STARTED,
        replayId = replayId ?: SentryId.EMPTY_ID,
        captureStrategy = strategy,
      )
    )

    registerRootViewListeners()
  }

  override fun resume() {
    enqueueOnMainThread {
      isManualPause = false
      resumeInternal()
    }
  }

  private fun resumeInternal() {
    val current = state.get()
    if (!isEnabled.get() || !current.lifecycleState.isAllowed(RESUMED)) {
      return
    }

    if (
      isManualPause ||
        lastKnownConnectionStatus == DISCONNECTED ||
        scopes?.rateLimiter?.isActiveForCategory(All) == true ||
        scopes?.rateLimiter?.isActiveForCategory(Replay) == true
    ) {
      return
    }

    current.captureStrategy?.resume()
    recorder?.resume()
    state.set(current.copy(lifecycleState = RESUMED))
  }

  override fun captureReplay(isTerminating: Boolean?): SentryId {
    val current = state.get()
    if (!isEnabled.get() || !current.isRecording) {
      return SentryId.EMPTY_ID
    }

    if (current.replayId == SentryId.EMPTY_ID) {
      options.logger.log(DEBUG, "Replay id is not set, not capturing for event")
      return SentryId.EMPTY_ID
    }

    if (current.isBuffering && !sample(options.sessionReplay.onErrorSampleRate)) {
      options.logger.log(
        INFO,
        "Replay wasn't sampled by onErrorSampleRate, not capturing for event",
      )
      return SentryId.EMPTY_ID
    }

    // Set it synchronously so the event that triggered the flush picks it up before conversion.
    scopes?.configureScope { it.replayId = current.replayId }
    if (isTerminating == true) {
      // A main-thread crash blocks the looper while flushing, so mark termination synchronously.
      current.captureStrategy?.captureReplay(true) {}
    } else {
      enqueueOnMainThread {
        captureReplayInternal(current.generation, current.replayId, false)
      }
    }
    return current.replayId
  }

  private fun captureReplayInternal(
    expectedGeneration: Long,
    expectedReplayId: SentryId,
    isTerminating: Boolean,
  ) {
    val current = state.get()
    val strategy = current.captureStrategy
    if (!current.matches(expectedGeneration, expectedReplayId) || strategy == null) {
      scopes?.configureScope {
        if (it.replayId == expectedReplayId) {
          it.replayId = SentryId.EMPTY_ID
        }
      }
      options.logger.log(
        INFO,
        "Replay was stopped or restarted before capture could run, not capturing for event",
      )
      return
    }

    var activeStrategy: CaptureStrategy = strategy
    strategy.captureReplay(
      isTerminating,
      onSegmentSent = { newTimestamp ->
        enqueueOnMainThread {
          val latest = state.get()
          // The flush completes asynchronously; ignore it if this replay was stopped, restarted,
          // or handed to another strategy in the meantime.
          if (
            latest.matches(expectedGeneration, expectedReplayId) &&
              latest.captureStrategy === activeStrategy
          ) {
            activeStrategy.currentSegment++
            activeStrategy.segmentTimestamp = newTimestamp
            activeStrategy.isFlushed = true
          }
        }
      },
    )
    activeStrategy = strategy.convert()
    val replayId: SentryId? = activeStrategy.currentReplayId
    state.set(
      current.copy(
        replayId = replayId ?: SentryId.EMPTY_ID,
        captureStrategy = activeStrategy,
      )
    )
  }

  override fun getReplayId(): SentryId = state.get().replayId

  override fun flush() {
    enqueueOnMainThread {
      val current = state.get()
      if (!current.isRecording) {
        startInternal(isFullSession = true)
      } else {
        captureReplayInternal(current.generation, current.replayId, false)
      }
    }
  }

  override fun setBreadcrumbConverter(converter: ReplayBreadcrumbConverter) {
    replayBreadcrumbConverter = converter
  }

  override fun getBreadcrumbConverter(): ReplayBreadcrumbConverter = replayBreadcrumbConverter

  override fun pause() {
    enqueueOnMainThread {
      isManualPause = true
      pauseInternal()
    }
  }

  override fun enableDebugMaskingOverlay() {
    debugMaskingEnabled = true
  }

  override fun disableDebugMaskingOverlay() {
    debugMaskingEnabled = false
  }

  override fun isDebugMaskingOverlayEnabled(): Boolean = debugMaskingEnabled

  override fun registerTraceId(traceId: SentryId) {
    val current = state.get()
    if (!isEnabled.get() || !current.isRecording) {
      return
    }
    current.captureStrategy?.registerTraceId(traceId)
  }

  override fun registerSegmentName(segmentName: String) {
    val current = state.get()
    if (!isEnabled.get() || !current.isRecording) {
      return
    }
    current.captureStrategy?.registerSegmentName(segmentName)
  }

  private fun pauseInternal() {
    val current = state.get()
    if (!isEnabled.get() || !current.lifecycleState.isAllowed(PAUSED)) {
      return
    }

    recorder?.pause()
    current.captureStrategy?.pause()
    state.set(current.copy(lifecycleState = PAUSED))
  }

  override fun stop() {
    enqueueOnMainThread { stopInternal() }
  }

  private fun stopInternal() {
    val current = state.get()
    if (!isEnabled.get() || !current.lifecycleState.isAllowed(STOPPED)) {
      return
    }

    unregisterRootViewListeners()
    recorder?.reset()
    recorder?.stop()
    gestureRecorder?.stop()
    current.captureStrategy?.stop()
    isManualPause = false
    state.set(
      current.copy(
        lifecycleState = STOPPED,
        replayId = SentryId.EMPTY_ID,
        captureStrategy = null,
      )
    )
  }

  override fun onScreenshotRecorded(bitmap: Bitmap) {
    var screen: String? = null
    scopes?.configureScope { screen = it.screen?.substringAfterLast('.') }
    state.get().captureStrategy?.onScreenshotRecorded(bitmap) { frameTimeStamp ->
      val observer = options.sessionReplay.frameObserver
      if (observer != null) {
        val copy = bitmap.copy(bitmap.config!!, false)
        if (copy != null) {
          try {
            val hint = Hint()
            hint.set(TypeCheckHint.REPLAY_FRAME_BITMAP, copy)
            observer.onMaskedFrameCaptured(hint, frameTimeStamp, screen)
          } catch (e: Throwable) {
            options.logger.log(ERROR, "Error in ReplayFrameObserver", e)
            copy.recycle()
          }
        }
      }
      addFrame(bitmap, frameTimeStamp, screen)
    }
    enqueueOnMainThread { checkCanRecord() }
  }

  override fun onScreenshotRecorded(screenshot: File, frameTimestamp: Long) {
    var screen: String? = null
    scopes?.configureScope { screen = it.screen?.substringAfterLast('.') }
    state.get().captureStrategy?.onScreenshotRecorded { _ ->
      val observer = options.sessionReplay.frameObserver
      if (observer != null) {
        val bitmap = BitmapFactory.decodeFile(screenshot.absolutePath)
        if (bitmap != null) {
          try {
            val hint = Hint()
            hint.set(TypeCheckHint.REPLAY_FRAME_BITMAP, bitmap)
            observer.onMaskedFrameCaptured(hint, frameTimestamp, screen)
          } catch (e: Throwable) {
            options.logger.log(ERROR, "Error in ReplayFrameObserver", e)
            bitmap.recycle()
          }
        }
      }
      addFrame(screenshot, frameTimestamp, screen)
    }
    enqueueOnMainThread { checkCanRecord() }
  }

  override fun close() {
    if (!isEnabled.get()) {
      return
    }

    val isMainThread = options.threadChecker.isMainThread
    if (isMainThread) {
      closeInternal()
      shutdownExecutors(waitForTermination = false)
      return
    }

    val closeCompleted = CountDownLatch(1)
    if (
      !mainLooperHandler.post {
        try {
          closeInternal()
        } finally {
          shutdownExecutors(waitForTermination = false)
          closeCompleted.countDown()
        }
      }
    ) {
      return
    }
    try {
      if (closeCompleted.await(options.shutdownTimeoutMillis, MILLISECONDS)) {
        shutdownExecutors(waitForTermination = true)
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun shutdownExecutors(waitForTermination: Boolean) {
    if (lazyReplayExecutor.isInitialized()) {
      if (waitForTermination) {
        replayExecutor.shutdown()
      } else {
        replayExecutor.gracefulShutdown()
      }
    }
    if (lazyPersistingExecutor.isInitialized()) {
      if (waitForTermination) {
        persistingExecutor.shutdown()
      } else {
        persistingExecutor.gracefulShutdown()
      }
    }
  }

  private fun closeInternal() {
    if (!state.get().lifecycleState.isAllowed(CLOSED)) {
      return
    }

    options.connectionStatusProvider.removeConnectionStatusObserver(this)
    scopes?.rateLimiter?.removeRateLimitObserver(this)
    stopInternal()
    recorder?.close()
    recorder = null
    rootViewsSpy.close()
    state.set(state.get().copy(lifecycleState = CLOSED))
  }

  override fun onConnectionStatusChanged(status: ConnectionStatus) {
    enqueueOnMainThread {
      lastKnownConnectionStatus = status
      if (state.get().captureStrategy !is SessionCaptureStrategy) {
        // we only want to stop recording when offline for session mode
        return@enqueueOnMainThread
      }

      if (status == DISCONNECTED) {
        pauseInternal()
      } else {
        // being positive for other states, even if it's NO_PERMISSION
        resumeInternal()
      }
    }
  }

  override fun onRateLimitChanged(rateLimiter: RateLimiter) {
    enqueueOnMainThread {
      if (state.get().captureStrategy !is SessionCaptureStrategy) {
        // we only want to stop recording when rate-limited for session mode
        return@enqueueOnMainThread
      }

      if (rateLimiter.isActiveForCategory(All) || rateLimiter.isActiveForCategory(Replay)) {
        pauseInternal()
      } else {
        resumeInternal()
      }
    }
  }

  override fun onTouchEvent(event: MotionEvent) {
    val current = state.get()
    if (!isEnabled.get() || !current.isTouchRecordingAllowed) {
      return
    }
    current.captureStrategy?.onTouchEvent(event)
  }

  // Lifecycle commands are always queued so calls from main cannot overtake earlier commands.
  private inline fun enqueueOnMainThread(crossinline block: () -> Unit) {
    mainLooperHandler.post { block() }
  }

  /**
   * Check if we're offline or rate-limited and pause for session mode to not overflow the envelope
   * cache.
   */
  private fun checkCanRecord() {
    if (
      state.get().captureStrategy is SessionCaptureStrategy &&
        (lastKnownConnectionStatus == DISCONNECTED ||
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
    if (!isEnabled.get() || !state.get().isRecording) {
      return
    }
    if (options.sessionReplay.isTrackConfiguration) {
      val recorderConfig =
        ScreenshotRecorderConfig.fromSize(context, options.sessionReplay, width, height)
      onConfigurationChanged(recorderConfig)
    }
  }

  public fun onConfigurationChanged(config: ScreenshotRecorderConfig) {
    val current = state.get()
    if (!isEnabled.get() || !current.isRecording) {
      return
    }
    current.captureStrategy?.onConfigurationChanged(config)
    recorder?.onConfigurationChanged(config)

    // we have to restart recorder with a new config and pause immediately if the replay is paused
    if (current.lifecycleState == PAUSED) {
      recorder?.pause()
    }
  }

  private fun sample(rate: Double?): Boolean =
    (random.get() ?: Random().also { random.set(it) }).sample(rate)

  private data class ReplayState(
    val generation: Long = 0,
    val lifecycleState: ReplayLifecycleState = ReplayLifecycleState.INITIAL,
    val replayId: SentryId = SentryId.EMPTY_ID,
    val captureStrategy: CaptureStrategy? = null,
  ) {
    val isBuffering: Boolean
      get() = captureStrategy is BufferCaptureStrategy

    val isRecording: Boolean
      get() = lifecycleState >= STARTED && lifecycleState < STOPPED

    val isTouchRecordingAllowed: Boolean
      get() = lifecycleState == STARTED || lifecycleState == RESUMED

    fun matches(generation: Long, replayId: SentryId): Boolean =
      isRecording && this.generation == generation && this.replayId == replayId
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

  private class ReplayPersistingExecutorServiceThreadFactory : ThreadFactory {
    private var cnt = 0

    override fun newThread(r: Runnable): Thread {
      val ret = Thread(r, "SentryReplayPersister-" + cnt++)
      ret.setDaemon(true)
      return ret
    }
  }
}
