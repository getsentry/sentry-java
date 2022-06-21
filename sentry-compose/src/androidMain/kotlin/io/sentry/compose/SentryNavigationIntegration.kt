package io.sentry.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.android.navigation.SentryNavigationListener

internal class SentryLifecycleObserver(
    private val navController: NavController,
    private val hub: IHub = HubAdapter.getInstance(),
    private val navListener: NavController.OnDestinationChangedListener =
        SentryNavigationListener(hub)
) : LifecycleEventObserver {

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

// As described in https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects#6
@Composable
@NonRestartableComposable
public fun NavHostController.withObservableEffect(): NavHostController {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, this) {
        val observer = SentryLifecycleObserver(this@withObservableEffect)

        lifecycle.addObserver(observer)

        onDispose {
            observer.dispose()
            lifecycle.removeObserver(observer)
        }
    }
    return this
}
