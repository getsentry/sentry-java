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
import io.sentry.ITransaction
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.android.navigation.SentryNavigationListener
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion

private const val TRACE_ORIGIN_APPENDIX = "jetpack_compose"

internal class SentryLifecycleObserver(
    private val navController: NavController,
    private val navListener: NavController.OnDestinationChangedListener =
        SentryNavigationListener(traceOriginAppendix = TRACE_ORIGIN_APPENDIX)
) : LifecycleEventObserver {

    private companion object {
        init {
            SentryIntegrationPackageStorage.getInstance().addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
        }
    }

    init {
        addIntegrationToSdkVersion("ComposeNavigation")
        SentryIntegrationPackageStorage.getInstance().addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            navController.addOnDestinationChangedListener(navListener)
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            navController.removeOnDestinationChangedListener(navListener)
        }
    }

    fun dispose() {
        navController.removeOnDestinationChangedListener(navListener)
    }
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends
 * them to Sentry for every navigation event when being attached to the respective [NavHostController].
 *
 * @param navListener An instance of a [SentryNavigationListener] that is shared with other sentry integrations, like
 * the fragment navigation integration.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withSentryObservableEffect(
    navListener: SentryNavigationListener
): NavHostController {
    val navListenerSnapshot by rememberUpdatedState(navListener)

    // As described in https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects#6
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, this) {
        val observer = SentryLifecycleObserver(
            this@withSentryObservableEffect,
            navListener = navListenerSnapshot
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
 * This version of withSentryObservableEffect should be used if you are working purely with Compose.
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

    return withSentryObservableEffect(
        navListener = SentryNavigationListener(
            enableNavigationBreadcrumbs = enableBreadcrumbsSnapshot,
            enableNavigationTracing = enableTracingSnapshot,
            traceOriginAppendix = TRACE_ORIGIN_APPENDIX
        )
    )
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
