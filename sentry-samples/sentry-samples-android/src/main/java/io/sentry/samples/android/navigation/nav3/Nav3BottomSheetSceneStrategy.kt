package io.sentry.samples.android.navigation.nav3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Sample-only scene strategies for rendering lightweight overlays inside the Nav3 content frame.
 */
internal data class Nav3OverlayScene<T : Any>(
  override val key: T,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val onBack: () -> Unit,
  private val alignment: Alignment,
  private val enterTransition: @Composable () -> androidx.compose.animation.EnterTransition,
  private val cardModifier: Modifier,
) : OverlayScene<T> {

  override val entries: List<NavEntry<T>> = listOf(entry)

  override val content: @Composable (() -> Unit) = {
    val visibleState = rememberOverlayVisibleState()

    // NavDisplay bases its system-back pop count on the non-overlay scene beneath this custom
    // in-content overlay. Consume Back here so a single press removes only the overlay entry.
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedOverlayScrim(visibleState = visibleState, onBack = onBack)
      AnimatedVisibility(
        visibleState = visibleState,
        enter = enterTransition(),
        modifier = Modifier.align(alignment),
      ) {
        Card(
          modifier = cardModifier,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
          entry.Content()
        }
      }
    }
  }
}

/** Displays entries with [bottomSheet] metadata as bottom-sheet overlays. */
internal class Nav3BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

  private val strategy =
    Nav3OverlaySceneStrategy<T>(
      metadataKey = BottomSheetKey,
      alignment = Alignment.BottomCenter,
      enterTransition = {
        fadeIn(animationSpec = tween(OVERLAY_ENTER_MILLIS)) + slideInVertically { it }
      },
      cardModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    return with(strategy) { calculateScene(entries) }
  }

  internal companion object {
    fun bottomSheet() = metadata { put(BottomSheetKey, true) }

    object BottomSheetKey : NavMetadataKey<Boolean>
  }
}

/** Displays marked Nav3 entries as in-content dialog overlays. */
@Composable
private fun rememberOverlayVisibleState(): MutableTransitionState<Boolean> = remember {
  MutableTransitionState(false).apply { targetState = true }
}

@Composable
private fun AnimatedOverlayScrim(
  visibleState: MutableTransitionState<Boolean>,
  onBack: () -> Unit,
) {
  AnimatedVisibility(
    visibleState = visibleState,
    enter = fadeIn(animationSpec = tween(OVERLAY_ENTER_MILLIS)),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { onBack() }
    )
  }
}

private const val OVERLAY_ENTER_MILLIS = 120

private class Nav3OverlaySceneStrategy<T : Any>(
  private val metadataKey: NavMetadataKey<Boolean>,
  private val alignment: Alignment,
  private val enterTransition: @Composable () -> androidx.compose.animation.EnterTransition,
  private val cardModifier: Modifier,
) {

  fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val overlayEntry = entries.lastOrNull() ?: return null
    overlayEntry.metadata[metadataKey] ?: return null
    val baseEntries = entries.dropLast(1)
    @Suppress("UNCHECKED_CAST") val sceneKey = overlayEntry.contentKey as T

    return Nav3OverlayScene<T>(
      key = sceneKey,
      previousEntries = baseEntries,
      overlaidEntries = baseEntries,
      entry = overlayEntry,
      onBack = onBack,
      alignment = alignment,
      enterTransition = enterTransition,
      cardModifier = cardModifier,
    )
  }
}

/** Displays entries with [dialog] metadata as dialog overlays inside the NavDisplay frame. */
internal class Nav3DialogSceneStrategy<T : Any> : SceneStrategy<T> {

  private val strategy =
    Nav3OverlaySceneStrategy<T>(
      metadataKey = DialogKey,
      alignment = Alignment.Center,
      enterTransition = {
        fadeIn(animationSpec = tween(OVERLAY_ENTER_MILLIS)) + scaleIn(initialScale = 0.96f)
      },
      cardModifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
    )

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    return with(strategy) { calculateScene(entries) }
  }

  internal companion object {
    fun dialog() = metadata { put(DialogKey, true) }

    object DialogKey : NavMetadataKey<Boolean>
  }
}
