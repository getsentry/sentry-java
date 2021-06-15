package io.sentry.android.fragment

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryLevel.INFO
import io.sentry.SpanStatus

@Suppress("TooManyFunctions")
class SentryFragmentLifecycleCallbacks(
    private val hub: IHub = HubAdapter.getInstance(),
    private val enableFragmentLifecycleBreadcrumbs: Boolean = true,
    performanceEnabled: Boolean = false,
    enableAutoFragmentLifecycleTracing: Boolean = false
) : FragmentLifecycleCallbacks() {

    constructor(
        enableFragmentLifecycleBreadcrumbs: Boolean,
        performanceEnabled: Boolean,
        enableAutoFragmentLifecycleTracing: Boolean
    ) : this(
        hub = HubAdapter.getInstance(),
        enableFragmentLifecycleBreadcrumbs = enableFragmentLifecycleBreadcrumbs,
        performanceEnabled = performanceEnabled,
        enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
    )

    private val isPerformanceEnabled = performanceEnabled && enableAutoFragmentLifecycleTracing

    private val fragmentsWithOngoingTransactions = mutableMapOf<Fragment, ISpan>()

    override fun onFragmentAttached(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        context: Context
    ) {
        addBreadcrumb(fragment, "attached")
    }

    override fun onFragmentSaveInstanceState(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        outState: Bundle
    ) {
        addBreadcrumb(fragment, "save instance state")
    }

    override fun onFragmentCreated(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        savedInstanceState: Bundle?
    ) {
        addBreadcrumb(fragment, "created")

        startTracing(fragment)
    }

    override fun onFragmentViewCreated(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        view: View,
        savedInstanceState: Bundle?
    ) {
        addBreadcrumb(fragment, "view created")
    }

    override fun onFragmentStarted(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "started")
    }

    override fun onFragmentResumed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "resumed")

        // ideally it should be post resumed
        stopTracing(fragment)
    }

    override fun onFragmentPaused(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "paused")
    }

    override fun onFragmentStopped(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "stopped")
    }

    override fun onFragmentViewDestroyed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "view destroyed")
    }

    override fun onFragmentDestroyed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "destroyed")

        stopTracing(fragment)
    }

    override fun onFragmentDetached(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "detached")
    }

    private fun addBreadcrumb(fragment: Fragment, state: String) {
        if (!enableFragmentLifecycleBreadcrumbs) {
            return
        }
        val breadcrumb = Breadcrumb().apply {
            type = "navigation"
            setData("state", state)
            setData("screen", getFragmentName(fragment))
            category = "ui.fragment.lifecycle"
            level = INFO
        }
        hub.addBreadcrumb(breadcrumb)
    }

    private fun getFragmentName(fragment: Fragment): String {
        return fragment.javaClass.simpleName
    }

    private fun isRunningSpan(fragment: Fragment): Boolean =
        fragmentsWithOngoingTransactions.containsKey(fragment)

    private fun startTracing(fragment: Fragment) {
        if (!isPerformanceEnabled || isRunningSpan(fragment)) {
            return
        }

        val currentSpan = hub.span

        val span: ISpan
        val fragmentName = getFragmentName(fragment)

        // should be a span of the activity transaction or its own transaction?
        span = currentSpan?.startChild(FRAGMENT_LOAD_OP, fragmentName)
            ?: hub.startTransaction(fragmentName, FRAGMENT_LOAD_OP)

        fragmentsWithOngoingTransactions[fragment] = span
    }

    private fun stopTracing(fragment: Fragment) {
        if (!isPerformanceEnabled || !isRunningSpan(fragment)) {
            return
        }

        val span = fragmentsWithOngoingTransactions[fragment]
        span?.let {
            var status = it.status
            if (status == null) {
                status = SpanStatus.OK
            }
            it.finish(status)
            fragmentsWithOngoingTransactions.remove(fragment)
        }
    }

    companion object {
        private const val FRAGMENT_LOAD_OP = "fragment.load"
    }
}
