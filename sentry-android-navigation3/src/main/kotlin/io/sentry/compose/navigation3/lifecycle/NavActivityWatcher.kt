package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * Watches every Activity of the host application and reports the Activity lifecycle events that
 * [NavLeaseCoordinator] acts on.
 *
 * - [onActivityResumed] is called when any Activity resumes, including Activities without a
 *   `SentryNavEffect`.
 *
 * **Lifetime**
 *
 * The callbacks are registered at most once per watcher and never unregistered. Like
 * `defaultNavLeaseCoordinator`, which owns the watcher used in practice, they're meant to live as
 * long as the process.
 */
internal class NavActivityWatcher(private val onActivityResumed: (activity: Activity) -> Unit) {

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

    override fun onActivityResumed(activity: Activity) {
      this@NavActivityWatcher.onActivityResumed(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
  }
}
