package io.sentry.android.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryLevel.INFO
import io.sentry.TypeCheckHint
import java.lang.ref.WeakReference

class SentryNavigationListener(
    private val hub: IHub = HubAdapter.getInstance()
) : NavController.OnDestinationChangedListener {

    private var fromRef: WeakReference<NavDestination>? = null
    private var fromArgs: Bundle? = null

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        addBreadcrumb(destination, arguments)
        fromRef = WeakReference(destination)
        fromArgs = arguments
    }

    private fun addBreadcrumb(destination: NavDestination, arguments: Bundle?) {
        val breadcrumb = Breadcrumb().apply {
            type = "navigation"
            category = "navigation"

            val from = fromRef?.get()?.route
            from?.let { data["from"] = it }
            fromArgs?.let { args ->
                data["from_arguments"] = args.keySet().filter {
                    it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
                }.associateWith { args[it] }
            }
            val to = destination.route
            to?.let { data["to"] = it }
            // TODO: should this be hidden behind Pii flag?
            arguments?.let { args ->
                data["to_arguments"] = args.keySet().filter {
                    it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
                }.associateWith { args[it] }
            }
            level = INFO
        }
        val hint = Hint()
        hint.set(TypeCheckHint.ANDROID_NAV_DESTINATION, destination)
        hub.addBreadcrumb(breadcrumb)
    }
}
