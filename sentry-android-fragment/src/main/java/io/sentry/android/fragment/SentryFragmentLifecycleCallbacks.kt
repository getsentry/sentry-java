package io.sentry.android.fragment

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel.INFO
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.ANDROID_FRAGMENT
import java.util.WeakHashMap

private const val TRACE_ORIGIN = "auto.ui.fragment"

@Suppress("TooManyFunctions")
class SentryFragmentLifecycleCallbacks(
    private val scopes: IScopes = ScopesAdapter.getInstance(),
    val filterFragmentLifecycleBreadcrumbs: Set<FragmentLifecycleState>,
    val enableAutoFragmentLifecycleTracing: Boolean
) : FragmentLifecycleCallbacks() {

    constructor(
        scopes: IScopes,
        enableFragmentLifecycleBreadcrumbs: Boolean,
        enableAutoFragmentLifecycleTracing: Boolean
    ) : this(
        scopes = scopes,
        filterFragmentLifecycleBreadcrumbs = FragmentLifecycleState.states
            .takeIf { enableFragmentLifecycleBreadcrumbs }
            .orEmpty(),
        enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
    )

    constructor(
        enableFragmentLifecycleBreadcrumbs: Boolean = true,
        enableAutoFragmentLifecycleTracing: Boolean = false
    ) : this(
        scopes = ScopesAdapter.getInstance(),
        filterFragmentLifecycleBreadcrumbs = FragmentLifecycleState.states
            .takeIf { enableFragmentLifecycleBreadcrumbs }
            .orEmpty(),
        enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
    )

    private val isPerformanceEnabled get() = scopes.options.isTracingEnabled && enableAutoFragmentLifecycleTracing

    private val fragmentsWithOngoingTransactions = WeakHashMap<Fragment, ISpan>()

    val enableFragmentLifecycleBreadcrumbs: Boolean
        get() = filterFragmentLifecycleBreadcrumbs.isNotEmpty()

    override fun onFragmentAttached(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        context: Context
    ) {
        addBreadcrumb(fragment, FragmentLifecycleState.ATTACHED)
    }

    override fun onFragmentSaveInstanceState(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        outState: Bundle
    ) {
        addBreadcrumb(fragment, FragmentLifecycleState.SAVE_INSTANCE_STATE)
    }

    override fun onFragmentCreated(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        savedInstanceState: Bundle?
    ) {
        addBreadcrumb(fragment, FragmentLifecycleState.CREATED)

        // we only start the tracing for the fragment if the fragment has been added to its activity
        // and not only to the backstack
        if (fragment.isAdded) {
            if (scopes.options.isEnableScreenTracking) {
                scopes.configureScope { it.screen = getFragmentName(fragment) }
            }
            startTracing(fragment)
        }
    }

    override fun onFragmentViewCreated(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        view: View,
        savedInstanceState: Bundle?
    ) {
        addBreadcrumb(fragment, FragmentLifecycleState.VIEW_CREATED)
    }

    override fun onFragmentStarted(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.STARTED)

        // ViewPager2 locks background fragments to STARTED state
        stopTracing(fragment)
    }

    override fun onFragmentResumed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.RESUMED)
    }

    override fun onFragmentPaused(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.PAUSED)
    }

    override fun onFragmentStopped(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.STOPPED)
    }

    override fun onFragmentViewDestroyed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.VIEW_DESTROYED)
    }

    override fun onFragmentDestroyed(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.DESTROYED)

        stopTracing(fragment)
    }

    override fun onFragmentDetached(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, FragmentLifecycleState.DETACHED)
    }

    private fun addBreadcrumb(fragment: Fragment, state: FragmentLifecycleState) {
        if (!filterFragmentLifecycleBreadcrumbs.contains(state)) {
            return
        }
        val breadcrumb = Breadcrumb().apply {
            type = "navigation"
            setData("state", state.breadcrumbName)
            setData("screen", getFragmentName(fragment))
            category = "ui.fragment.lifecycle"
            level = INFO
        }

        val hint = Hint()
            .also { it.set(ANDROID_FRAGMENT, fragment) }

        scopes.addBreadcrumb(breadcrumb, hint)
    }

    private fun getFragmentName(fragment: Fragment): String {
        return fragment.javaClass.canonicalName ?: fragment.javaClass.simpleName
    }

    private fun isRunningSpan(fragment: Fragment): Boolean =
        fragmentsWithOngoingTransactions.containsKey(fragment)

    private fun startTracing(fragment: Fragment) {
        if (!isPerformanceEnabled || isRunningSpan(fragment)) {
            return
        }

        var transaction: ISpan? = null
        scopes.configureScope {
            transaction = it.transaction
        }

        val fragmentName = getFragmentName(fragment)
        val span = transaction?.startChild(FRAGMENT_LOAD_OP, fragmentName)

        span?.let {
            fragmentsWithOngoingTransactions[fragment] = it
            it.spanContext.origin = TRACE_ORIGIN
        }
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
        const val FRAGMENT_LOAD_OP = "ui.load"
    }
}
