package io.sentry.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.ITransaction
import io.sentry.IntegrationName
import io.sentry.SentryOptions
import io.sentry.android.navigation.SentryNavigationListener

internal class SentryLifecycleObserver(
    private val navController: NavController,
    private val navListener: NavController.OnDestinationChangedListener =
        SentryNavigationListener()
) : LifecycleEventObserver, IntegrationName {

    init {
        HubAdapter.getInstance().options.sdkVersion?.let { sdkVersion ->
            addIntegrationToSdkVersion()
            sdkVersion.addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            navController.addOnDestinationChangedListener(navListener)
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            navController.removeOnDestinationChangedListener(navListener)
        }
    }

    override fun getIntegrationName(): String {
        return "ComposeNavigation"
    }

    fun dispose() {
        navController.removeOnDestinationChangedListener(navListener)
    }
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends
 * them to Sentry for every navigation event when being attached to the respective [NavHostController].
 *
 * @param enableNavigationBreadcrumbs Whether the integration should capture breadcrumbs for
 * navigation events.
 * @param enableNavigationTracing Whether the integration should start a new [ITransaction]
 * with [SentryOptions.idleTimeout] for navigation events.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withSentryObservableEffect(
    enableNavigationBreadcrumbs: Boolean = true,
    enableNavigationTracing: Boolean = true
): NavHostController {
    val enableBreadcrumbsSnapshot by rememberUpdatedState(enableNavigationBreadcrumbs)
    val enableTracingSnapshot by rememberUpdatedState(enableNavigationTracing)

    // As described in https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects#6
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, this) {
        val observer = SentryLifecycleObserver(
            this@withSentryObservableEffect,
            navListener = SentryNavigationListener(
                enableNavigationBreadcrumbs = enableBreadcrumbsSnapshot,
                enableNavigationTracing = enableTracingSnapshot
            )
        )

        lifecycle.addObserver(observer)

        onDispose {
            observer.dispose()
            lifecycle.removeObserver(observer)
        }
    }
    return this
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends
 * them to Sentry for every navigation event when being attached to the respective [NavHostController].
 *
 * Used by the sentry android gradle plugin for Jetpack Compose instrumentation.
 *
 */
@Composable
internal fun NavHostController.withSentryObservableEffect(): NavHostController {
    return withSentryObservableEffect(
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = true
    )
}
