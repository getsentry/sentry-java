package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle

/**
 * Watches every Activity of the host application and reports the Activity lifecycle events that
 * [NavLeaseCoordinator] acts on, so that the coordinator needn't know how they differ across API
 * levels.
 *
 * Each event is reported relative to `ActivityLifecycleIntegration`, which binds an Activity's
 * `ui.load` transaction to the scope when the Activity is created and removes it when the Activity
 * is destroyed:
 *
 * - [onActivityCreatedOrResumed] is called when an Activity is created and again when it resumes,
 *   with [ActivityEvent.CREATED] or [ActivityEvent.RESUMED] as its event. On API 29+, creation is
 *   reported before `ActivityLifecycleIntegration` tries to bind the Activity's `ui.load`
 *   transaction; below API 29, after. So below API 29, a withdrawal on creation comes too late for
 *   that `ui.load` transaction, which stays unbound (see "Non-candidate roots and lease withdrawal"
 *   in the package README).
 *
 * - [onActivityDestroyed] is called when an Activity is destroyed, after
 *   `ActivityLifecycleIntegration` has removed the Activity's `ui.load` transaction from the scope.
 *
 * **Android ordering assumptions**
 *
 * The watcher relies on the following callback orders, which Android doesn't document but AOSP has
 * historically upheld:
 *
 * - `Application` calls `ActivityLifecycleCallbacks` in registration order. Below API 29,
 *   `ActivityLifecycleIntegration`'s callbacks run before this class's, provided the SDK is
 *   initialized before this class starts [watching][watch].
 *
 * - On API 29+, every `onActivityPreCreated` callback runs before any `onActivityCreated` callback,
 *   and every `onActivityDestroyed` callback before any `onActivityPostDestroyed` callback.
 *
 * **Lifetime**
 *
 * The callbacks are registered at most once per watcher and never unregistered. Like
 * `defaultNavLeaseCoordinator`, which owns the watcher used in practice, they're meant to live as
 * long as the process.
 */
internal class NavActivityWatcher(
  private val onActivityCreatedOrResumed: (activity: Activity, event: ActivityEvent) -> Unit,
  private val onActivityDestroyed: (activity: Activity) -> Unit,
) {

  /**
   * The Activity lifecycle event passed to [onActivityCreatedOrResumed], described by [description]
   * in past tense (e.g., "created") for log messages.
   */
  enum class ActivityEvent(val description: String) {
    CREATED("created"),
    RESUMED("resumed"),
  }

  private var isWatching = false

  /** Starts watching the Activities of [application]. Does nothing if already watching. */
  fun watch(application: Application) {
    if (isWatching) {
      return
    }
    isWatching = true
    application.registerActivityLifecycleCallbacks(Callbacks())
  }

  private inner class Callbacks : ActivityLifecycleCallbacks {

    // Only called on API 29+, before any callback's onActivityCreated.
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
      onActivityCreatedOrResumed(activity, ActivityEvent.CREATED)
    }

    // Covers API levels below 29, as they don't call onActivityPreCreated. Runs after
    // ActivityLifecycleIntegration's onActivityCreated, which finds the slot taken and never binds
    // the Activity's ui.load transaction. This is a known limitation (see the package README).
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        onActivityCreatedOrResumed(activity, ActivityEvent.CREATED)
      }
    }

    override fun onActivityResumed(activity: Activity) {
      onActivityCreatedOrResumed(activity, ActivityEvent.RESUMED)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // Covers API levels below 29, which don't call onActivityPostDestroyed.
    override fun onActivityDestroyed(activity: Activity) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        this@NavActivityWatcher.onActivityDestroyed(activity)
      }
    }

    // Only called on API 29+, after every callback's onActivityDestroyed.
    override fun onActivityPostDestroyed(activity: Activity) {
      this@NavActivityWatcher.onActivityDestroyed(activity)
    }
  }
}
