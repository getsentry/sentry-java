package io.sentry.android.compose

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.sentry.HubAdapter
import io.sentry.IHub

internal class ComposeNavigationObserver(
    private val navController: NavController,
    private val hub: IHub = HubAdapter.getInstance()
) : LifecycleEventObserver, NavController.OnDestinationChangedListener {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            navController.addOnDestinationChangedListener(this)
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            navController.removeOnDestinationChangedListener(this)
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
    }

    fun dispose() {
        navController.removeOnDestinationChangedListener(this)
    }
}

@Composable
@NonRestartableComposable
fun NavController.withObservableEffect() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, this) {
        val observer = ComposeNavigationObserver(this@withObservableEffect)

        lifecycle.addObserver(observer)

        onDispose {
            observer.dispose()
            lifecycle.removeObserver(observer)
        }
    }
}
