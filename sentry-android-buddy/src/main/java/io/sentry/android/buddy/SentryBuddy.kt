package io.sentry.android.buddy

import android.app.Application
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
public object SentryBuddy {
  private val lock: Any = Any()
  private var recorder: BuddyRecorder? = null
  private var lifecycleCallbacks: BuddyActivityLifecycleCallbacks? = null
  private var installedApplication: Application? = null

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

      recorder = newRecorder
      lifecycleCallbacks = callbacks
      installedApplication = application
    }
  }

  @JvmStatic
  public fun startRecording(intent: BuddyFlowIntent) {
    requireInstalled().start(intent)
  }

  @JvmStatic
  public fun recordStep(name: String) {
    recordStep(name, emptyMap())
  }

  @JvmStatic
  public fun recordStep(name: String, data: Map<String, Any?>) {
    requireInstalled().recordStep(name, data)
  }

  @JvmStatic
  public fun stopRecording(): BuddyFlowRecording {
    return requireInstalled().stop()
  }

  private fun requireInstalled(): BuddyRecorder {
    return checkNotNull(recorder) { "SentryBuddy.install(application) must be called first." }
  }

  private fun uninstallLocked() {
    lifecycleCallbacks?.let { callbacks ->
      callbacks.detachAll()
      installedApplication?.unregisterActivityLifecycleCallbacks(callbacks)
    }
    recorder = null
    lifecycleCallbacks = null
    installedApplication = null
  }

  private fun overlayManager(options: SentryBuddyOptions): BuddyOverlayManager? {
    if (!options.showOverlay) {
      return null
    }
    return BuddyOverlayManager(
      SentryBuddySessionController(flowAnalysesApi = options.flowAnalysesApi)
    )
  }

  @TestOnly
  internal fun resetForTest() {
    synchronized(lock) {
      uninstallLocked()
    }
  }
}
