package io.sentry.android.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryLevel.INFO
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

            val from = fromRef?.get() ?: "/" // similar to what sentry-dart does
            data["from"] = from
            fromArgs?.let { args ->
                data["from_arguments"] = args.keySet().associateWith { args[it] }
            }
            val to = destination.route ?: "/"
            data["to"] = to
            arguments?.let { args ->
                data["to_arguments"] = args.keySet().associateWith { args[it] }
            }
            level = INFO
        }
//    val hint = Hint()
//    hint.set(ANDROID_ACTIVITY, activity)
        hub.addBreadcrumb(breadcrumb)
    }
}
