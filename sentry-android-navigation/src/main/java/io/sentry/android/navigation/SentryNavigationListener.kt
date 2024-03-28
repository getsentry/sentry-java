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
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.TracingUtils
import java.lang.ref.WeakReference

private const val TRACE_ORIGIN = "auto.navigation"

/**
 * A [NavController.OnDestinationChangedListener] that captures a [Breadcrumb] and starts an
 * [ITransaction] and sends them to Sentry for each [onDestinationChanged] call.
 *
 * @param enableNavigationBreadcrumbs Whether the integration should capture breadcrumbs for
 * navigation events.
 * @param enableNavigationTracing Whether the integration should start a new idle [ITransaction]
 * with [SentryOptions.idleTimeout] for navigation events.
 */
public class SentryNavigationListener @JvmOverloads constructor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val enableNavigationBreadcrumbs: Boolean = true,
    private val enableNavigationTracing: Boolean = true,
    private val traceOriginAppendix: String? = null
) : NavController.OnDestinationChangedListener {

    private var previousDestinationRef: WeakReference<NavDestination>? = null
    private var previousArgs: Bundle? = null

    private val isPerformanceEnabled get() = hub.options.isTracingEnabled && enableNavigationTracing

    private var activeTransaction: ITransaction? = null

    init {
        addIntegrationToSdkVersion(javaClass)
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-android-navigation", BuildConfig.VERSION_NAME)
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val toArguments = arguments.refined()

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
            val fromArguments = previousArgs.refined()
            if (fromArguments.isNotEmpty()) {
                data["from_arguments"] = fromArguments
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
        hub.addBreadcrumb(breadcrumb, hint)
    }

    private fun startTracing(
        controller: NavController,
        destination: NavDestination,
        arguments: Map<String, Any?>
    ) {
        if (!isPerformanceEnabled) {
            TracingUtils.startNewTrace(hub)
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

        val transactionOptions = TransactionOptions().also {
            it.isWaitForChildren = true
            it.idleTimeout = hub.options.idleTimeout
            it.deadlineTimeout = TransactionOptions.DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION
            it.isTrimEnd = true
        }

        val transaction = hub.startTransaction(
            TransactionContext(name, TransactionNameSource.ROUTE, NAVIGATION_OP),
            transactionOptions
        )

        transaction.spanContext.origin = traceOriginAppendix?.let {
            "$TRACE_ORIGIN.$traceOriginAppendix"
        } ?: TRACE_ORIGIN

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

    private fun Bundle?.refined(): Map<String, Any?> =
        this?.let { args ->
            args.keySet().filter {
                it != NavController.KEY_DEEP_LINK_INTENT // there's a lot of unrelated stuff
            }.associateWith { args[it] }
        } ?: emptyMap()

    public companion object {
        public const val NAVIGATION_OP: String = "navigation"
    }
}
