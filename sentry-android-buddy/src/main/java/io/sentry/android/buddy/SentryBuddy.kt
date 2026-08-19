package io.sentry.android.buddy

import android.app.Application
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.buddy.model.BuddyFlowIntent
import io.sentry.android.buddy.model.BuddyFlowRecording
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.ui.overlay.BuddyOverlayManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
public object SentryBuddy {
  private val lock: Any = Any()
  private var recorder: BuddyRecorder? = null
  private var lifecycleCallbacks: BuddyActivityLifecycleCallbacks? = null
  private var installedApplication: Application? = null
  private var previousBeforeSend: SentryOptions.BeforeSendCallback? = null
  private var buddyBeforeSend: SentryOptions.BeforeSendCallback? = null
  private var previousBeforeSendTransaction: SentryOptions.BeforeSendTransactionCallback? = null
  private var buddyBeforeSendTransaction: SentryOptions.BeforeSendTransactionCallback? = null
  private var previousBeforeBreadcrumb: SentryOptions.BeforeBreadcrumbCallback? = null
  private var buddyBeforeBreadcrumb: SentryOptions.BeforeBreadcrumbCallback? = null
  private var previousTracesSampler: SentryOptions.TracesSamplerCallback? = null
  private var buddyTracesSampler: SentryOptions.TracesSamplerCallback? = null

  @JvmStatic
  public fun install(application: Application) {
    install(application, SentryBuddyOptions())
  }

  @JvmStatic
  public fun install(application: Application, configure: SentryBuddyOptions.() -> Unit) {
    install(application, SentryBuddyOptions().apply(configure))
  }

  @JvmStatic
  public fun install(application: Application, options: SentryBuddyOptions) {
    synchronized(lock) {
      if (!options.enabled) {
        uninstallLocked()
        return
      }

      if (installedApplication === application && recorder != null) {
        lifecycleCallbacks?.updateOverlay(options)
        return
      }
      uninstallLocked()

      val sentryFacade = RealBuddySentryFacade()
      val newRecorder =
        BuddyRecorder(
          metadataProvider = AndroidBuddyMetadataProvider(application, sentryFacade),
          sentryFacade = sentryFacade,
        )
      val callbacks = BuddyActivityLifecycleCallbacks(newRecorder, overlayManager(options))
      application.registerActivityLifecycleCallbacks(callbacks)
      installEventObserver(newRecorder)
      installBreadcrumbObserver(newRecorder)
      installTransactionObserver(newRecorder)
      installTracesSampler(newRecorder)

      recorder = newRecorder
      lifecycleCallbacks = callbacks
      installedApplication = application
    }
  }

  @JvmStatic
  public fun startRecording(intent: BuddyFlowIntent) {
    val recorder = requireInstalled()
    recorder.start(intent)
    lifecycleCallbacks?.recordCurrentScreen()
    lifecycleCallbacks?.recordingEvent("Flow recording started")
  }

  @JvmStatic
  public fun recordStep(name: String) {
    recordStep(name, emptyMap())
  }

  @JvmStatic
  public fun recordStep(name: String, data: Map<String, Any?>) {
    requireInstalled().recordStep(name, data)
    lifecycleCallbacks?.recordingEvent("Step: $name")
  }

  @JvmStatic
  public fun stopRecording(): BuddyFlowRecording {
    return requireInstalled().stop()
  }

  internal fun liveFeedSnapshot(): BuddyLiveFeed = requireInstalled().liveFeedSnapshot()

  internal fun markLiveFeedSeen(): BuddyLiveFeed = requireInstalled().markLiveFeedSeen()

  internal fun addLiveFeedListener(listener: (BuddyLiveFeed) -> Unit): () -> Unit =
    requireInstalled().addLiveFeedListener(listener)

  private fun requireInstalled(): BuddyRecorder {
    return checkNotNull(recorder) { "SentryBuddy.install(application) must be called first." }
  }

  private fun uninstallLocked() {
    restoreTracesSampler()
    restoreTransactionObserver()
    restoreBreadcrumbObserver()
    restoreEventObserver()
    lifecycleCallbacks?.let { callbacks ->
      callbacks.detachAll()
      installedApplication?.unregisterActivityLifecycleCallbacks(callbacks)
    }
    recorder = null
    lifecycleCallbacks = null
    installedApplication = null
  }

  private fun installTransactionObserver(recorder: BuddyRecorder) {
    val sentryOptions = Sentry.getCurrentScopes().options
    val original = sentryOptions.beforeSendTransaction
    val observer = RealBuddySentryFacade.transactionObserver(recorder, original)
    previousBeforeSendTransaction = original
    buddyBeforeSendTransaction = observer
    sentryOptions.beforeSendTransaction = observer
  }

  private fun installEventObserver(recorder: BuddyRecorder) {
    val sentryOptions = Sentry.getCurrentScopes().options
    val original = sentryOptions.beforeSend
    val observer = RealBuddySentryFacade.eventObserver(recorder, original)
    previousBeforeSend = original
    buddyBeforeSend = observer
    sentryOptions.beforeSend = observer
  }

  private fun installBreadcrumbObserver(recorder: BuddyRecorder) {
    val sentryOptions = Sentry.getCurrentScopes().options
    val original = sentryOptions.beforeBreadcrumb
    val observer = RealBuddySentryFacade.breadcrumbObserver(recorder, original)
    previousBeforeBreadcrumb = original
    buddyBeforeBreadcrumb = observer
    sentryOptions.beforeBreadcrumb = observer
  }

  private fun installTracesSampler(recorder: BuddyRecorder) {
    val sentryOptions = Sentry.getCurrentScopes().options
    val original = sentryOptions.tracesSampler
    val sampler = RealBuddySentryFacade.tracesSampler(recorder, original)
    previousTracesSampler = original
    buddyTracesSampler = sampler
    sentryOptions.tracesSampler = sampler
  }

  private fun restoreTransactionObserver() {
    val observer = buddyBeforeSendTransaction ?: return
    val sentryOptions = Sentry.getCurrentScopes().options
    if (sentryOptions.beforeSendTransaction === observer) {
      sentryOptions.beforeSendTransaction = previousBeforeSendTransaction
    }
    previousBeforeSendTransaction = null
    buddyBeforeSendTransaction = null
  }

  private fun restoreEventObserver() {
    val observer = buddyBeforeSend ?: return
    val sentryOptions = Sentry.getCurrentScopes().options
    if (sentryOptions.beforeSend === observer) {
      sentryOptions.beforeSend = previousBeforeSend
    }
    previousBeforeSend = null
    buddyBeforeSend = null
  }

  private fun restoreBreadcrumbObserver() {
    val observer = buddyBeforeBreadcrumb ?: return
    val sentryOptions = Sentry.getCurrentScopes().options
    if (sentryOptions.beforeBreadcrumb === observer) {
      sentryOptions.beforeBreadcrumb = previousBeforeBreadcrumb
    }
    previousBeforeBreadcrumb = null
    buddyBeforeBreadcrumb = null
  }

  private fun restoreTracesSampler() {
    val sampler = buddyTracesSampler ?: return
    val sentryOptions = Sentry.getCurrentScopes().options
    if (sentryOptions.tracesSampler === sampler) {
      sentryOptions.tracesSampler = previousTracesSampler
    }
    previousTracesSampler = null
    buddyTracesSampler = null
  }

  private fun overlayManager(options: SentryBuddyOptions): BuddyOverlayManager? {
    if (!options.showOverlay) {
      return null
    }
    return BuddyOverlayManager(
        SentryBuddySessionController(
          flowAnalysesApi = options.flowAnalysesApi,
          healthCheckApi = options.healthCheckApi,
          openUrlApi = options.openUrlApi,
        )
      )
      .also { it.updateOptions(options) }
  }

  @TestOnly
  internal fun resetForTest() {
    synchronized(lock) {
      uninstallLocked()
    }
  }
}
