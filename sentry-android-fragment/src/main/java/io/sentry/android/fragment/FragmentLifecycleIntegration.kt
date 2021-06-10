package io.sentry.android.fragment

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.Integration
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryOptions
import java.io.Closeable

class FragmentLifecycleIntegration(private val application: Application) :
    ActivityLifecycleCallbacks,
    Integration,
    Closeable {

    private lateinit var hub: IHub
    private lateinit var logger: ILogger

    override fun register(hub: IHub, options: SentryOptions) {
        this.hub = hub
        this.logger = options.logger

        application.registerActivityLifecycleCallbacks(this)
        logger.log(DEBUG, "FragmentLifecycleIntegration installed.")
    }

    override fun close() {
        application.unregisterActivityLifecycleCallbacks(this)
        logger.log(DEBUG, "FragmentLifecycleIntegration removed.")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        (activity as? FragmentActivity)
            ?.supportFragmentManager
            ?.registerFragmentLifecycleCallbacks(
                SentryFragmentLifecycleCallbacks(hub),
                true
            )
    }

    override fun onActivityStarted(activity: Activity) {
        // no-op
    }

    override fun onActivityResumed(activity: Activity) {
        // no-op
    }

    override fun onActivityPaused(activity: Activity) {
        // no-op
    }

    override fun onActivityStopped(activity: Activity) {
        // no-op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // no-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // no-op
    }
}
