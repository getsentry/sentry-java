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
import io.sentry.SentryLevel.INFO

@Suppress("TooManyFunctions")
class SentryFragmentLifecycleCallbacks(
    private val hub: IHub = HubAdapter.getInstance()
) : FragmentLifecycleCallbacks() {

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
    }

    override fun onFragmentDetached(fragmentManager: FragmentManager, fragment: Fragment) {
        addBreadcrumb(fragment, "detached")
    }

    private fun addBreadcrumb(fragment: Fragment, state: String) {
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
}
