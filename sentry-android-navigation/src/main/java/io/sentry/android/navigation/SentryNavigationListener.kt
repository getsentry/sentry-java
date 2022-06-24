package io.sentry.android.navigation

import android.content.res.Resources.NotFoundException
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ITransaction
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint
import java.lang.ref.WeakReference

class SentryNavigationListener @JvmOverloads constructor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val enableNavigationBreadcrumbs: Boolean = true,
    private val enableNavigationTracing: Boolean = true
) : NavController.OnDestinationChangedListener {

    private var previousDestinationRef: WeakReference<NavDestination>? = null
    private var previousArgs: Bundle? = null

    private val isPerformanceEnabled get() = hub.options.isTracingEnabled && enableNavigationTracing

    private var activeTransaction: ITransaction? = null

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val toArguments = arguments?.let { args ->
            args.keySet().filter {
                it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
            }.associateWith { args[it] }
        } ?: emptyMap()

        addBreadcrumb(destination, toArguments)
        startTracing(controller, destination, toArguments)
        previousDestinationRef = WeakReference(destination)
        previousArgs = arguments
    }

    private fun addBreadcrumb(destination: NavDestination, arguments: Map<String, Any?>) {
        if (!enableNavigationBreadcrumbs) {
            return
        }
        val breadcrumb = Breadcrumb().apply {
            type = NAVIGATION_OP
            category = NAVIGATION_OP

            val from = previousDestinationRef?.get()?.route
            from?.let { data["from"] = "/$it" }
            previousArgs?.let { args ->
                val fromArguments = args.keySet().filter {
                    it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
                }.associateWith { args[it] }
                if (fromArguments.isNotEmpty()) {
                    data["from_arguments"] = fromArguments
                }
            }

            val to = destination.route
            to?.let { data["to"] = "/$it" }
            if (arguments.isNotEmpty()) {
                data["to_arguments"] = arguments
            }

            level = INFO
        }
        val hint = Hint()
        hint.set(TypeCheckHint.ANDROID_NAV_DESTINATION, destination)
        hub.addBreadcrumb(breadcrumb)
    }

    private fun startTracing(
        controller: NavController,
        destination: NavDestination,
        arguments: Map<String, Any?>
    ) {
        if (!isPerformanceEnabled) {
            return
        }

        // we can only have one nav transaction at a time
        if (activeTransaction != null) {
            stopTracing()
        }

        if (destination.navigatorName == "activity") {
            // we do not trace navigation between activities to avoid clashing with activity lifecycle tracing
            hub.options.logger.log(
                DEBUG,
                "Navigating to activity destination, no transaction captured."
            )
            return
        }

        @Suppress("SwallowedException") // we swallow it on purpose
        var name = destination.route ?: try {
            controller.context.resources.getResourceEntryName(destination.id)
        } catch (e: NotFoundException) {
            hub.options.logger.log(
                DEBUG,
                "Destination id cannot be retrieved from Resources, no transaction captured."
            )
            return
        }

        // we add '/' to the name to match dart and web pattern
        name = "/" + name.substringBefore('/') // strip out arguments from the tx name

        val transaction =
            hub.startTransaction(name, NAVIGATION_OP, true, hub.options.idleTimeout, true)

        if (arguments.isNotEmpty()) {
            transaction.setData("arguments", arguments)
        }
        hub.configureScope { scope ->
            scope.withTransaction { tx ->
                if (tx == null) {
                    scope.transaction = transaction
                }
            }
        }
        activeTransaction = transaction
    }

    private fun stopTracing() {
        val status = activeTransaction?.status ?: SpanStatus.OK
        activeTransaction?.finish(status)

        // clear transaction from scope so others can bind to it
        hub.configureScope { scope ->
            scope.withTransaction { tx ->
                if (tx == activeTransaction) {
                    scope.clearTransaction()
                }
            }
        }

        activeTransaction = null
    }

    companion object {
        const val NAVIGATION_OP = "navigation"
    }
}
