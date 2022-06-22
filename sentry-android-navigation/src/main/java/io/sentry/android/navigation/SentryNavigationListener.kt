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

    private var previousDestinationRef: WeakReference<NavDestination>? = null
    private var previousArgs: Bundle? = null

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        addBreadcrumb(destination, arguments)
        previousDestinationRef = WeakReference(destination)
        previousArgs = arguments
    }

    private fun addBreadcrumb(destination: NavDestination, arguments: Bundle?) {
        val breadcrumb = Breadcrumb().apply {
            type = "navigation"
            category = "navigation"

            val from = previousDestinationRef?.get()?.route
            from?.let { data["from"] = it }
            previousArgs?.let { args ->
                val fromArguments = args.keySet().filter {
                    it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
                }.associateWith { args[it] }
                if (fromArguments.isNotEmpty()) {
                    data["from_arguments"] = fromArguments
                }
            }

            val to = destination.route
            to?.let { data["to"] = it }
            arguments?.let { args ->
                val toArguments = args.keySet().filter {
                    it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
                }.associateWith { args[it] }
                if (toArguments.isNotEmpty()) {
                    data["to_arguments"] = toArguments
                }
            }

            level = INFO
        }
        val hint = Hint()
        hint.set(TypeCheckHint.ANDROID_NAV_DESTINATION, destination)
        hub.addBreadcrumb(breadcrumb)
    }
}
