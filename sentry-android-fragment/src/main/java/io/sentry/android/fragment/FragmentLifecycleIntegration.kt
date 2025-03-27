package io.sentry.android.fragment

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryOptions
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.io.Closeable

public class FragmentLifecycleIntegration(
    private val application: Application,
    private val filterFragmentLifecycleBreadcrumbs: Set<FragmentLifecycleState>,
    private val enableAutoFragmentLifecycleTracing: Boolean
) :
    ActivityLifecycleCallbacks,
    Integration,
    Closeable {

    private companion object {
        init {
            SentryIntegrationPackageStorage.getInstance()
                .addPackage("maven:io.sentry:sentry-android-fragment", BuildConfig.VERSION_NAME)
        }
    }

    public constructor(application: Application) : this(
        application = application,
        filterFragmentLifecycleBreadcrumbs = FragmentLifecycleState.states,
        enableAutoFragmentLifecycleTracing = false
    )

    public constructor(
        application: Application,
        enableFragmentLifecycleBreadcrumbs: Boolean,
        enableAutoFragmentLifecycleTracing: Boolean
    ) : this(
        application = application,
        filterFragmentLifecycleBreadcrumbs = FragmentLifecycleState.states
            .takeIf { enableFragmentLifecycleBreadcrumbs }
            .orEmpty(),
        enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
    )

    private lateinit var scopes: IScopes
    private lateinit var options: SentryOptions

    override fun register(scopes: IScopes, options: SentryOptions) {
        this.scopes = scopes
        this.options = options

        application.registerActivityLifecycleCallbacks(this)
        options.logger.log(DEBUG, "FragmentLifecycleIntegration installed.")
        addIntegrationToSdkVersion("FragmentLifecycle")
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-android-fragment", BuildConfig.VERSION_NAME)
    }

    override fun close() {
        application.unregisterActivityLifecycleCallbacks(this)
        if (::options.isInitialized) {
            options.logger.log(DEBUG, "FragmentLifecycleIntegration removed.")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        (activity as? FragmentActivity)
            ?.supportFragmentManager
            ?.registerFragmentLifecycleCallbacks(
                SentryFragmentLifecycleCallbacks(
                    scopes = scopes,
                    filterFragmentLifecycleBreadcrumbs = filterFragmentLifecycleBreadcrumbs,
                    enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
                ),
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
        /**
         * It is not needed to unregister [SentryFragmentLifecycleCallbacks] as
         * [androidx.fragment.app.FragmentManager] will do this on its own when it's destroyed.
         *
         * @see [androidx.fragment.app.FragmentManager.registerFragmentLifecycleCallbacks]
         */
        // no-op
    }
}
