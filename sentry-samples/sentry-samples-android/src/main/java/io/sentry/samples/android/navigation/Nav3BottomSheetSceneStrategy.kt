/*
 * Adapted from https://github.com/android/nav3-recipes/blob/main/app/src/main/java/com/example/nav3recipes/bottomsheet/BottomSheetSceneStrategy.kt
 * Copyright (C) 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package io.sentry.samples.android.navigation

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

/** Displays marked Nav3 entries as in-content bottom-sheet overlays. */
internal data class Nav3BottomSheetScene<T : Any>(
  override val key: T,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val onBack: () -> Unit,
) : OverlayScene<T> {

  override val entries: List<NavEntry<T>> = listOf(entry)

  override val content: @Composable (() -> Unit) = {
    val visibleState = rememberOverlayVisibleState()

    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedOverlayScrim(visibleState = visibleState, onBack = onBack)
      AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(OVERLAY_ENTER_MILLIS)) + slideInVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
      ) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val lastEntry = entries.lastOrNull() ?: return null
    lastEntry.metadata[BottomSheetKey] ?: return null

    @Suppress("UNCHECKED_CAST")
    return Nav3BottomSheetScene(
      key = lastEntry.contentKey as T,
      previousEntries = entries.dropLast(1),
      overlaidEntries = entries.dropLast(1),
      entry = lastEntry,
      onBack = onBack,
    )
  }

  internal companion object {
    fun bottomSheet() = metadata { put(BottomSheetKey, true) }

    object BottomSheetKey : NavMetadataKey<Boolean>
  }
}

/** Displays marked Nav3 entries as in-content dialog overlays. */
internal data class Nav3DialogScene<T : Any>(
  override val key: T,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val onBack: () -> Unit,
) : OverlayScene<T> {

  override val entries: List<NavEntry<T>> = listOf(entry)

  override val content: @Composable (() -> Unit) = {
    val visibleState = rememberOverlayVisibleState()

    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedOverlayScrim(visibleState = visibleState, onBack = onBack)
      AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(OVERLAY_ENTER_MILLIS)) + scaleIn(initialScale = 0.96f),
        modifier = Modifier.align(Alignment.Center),
      ) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
          entry.Content()
        }
      }
    }
  }
}

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

private const val OVERLAY_ENTER_MILLIS = 180

/** Displays entries with [dialog] metadata as dialog overlays inside the NavDisplay frame. */
internal class Nav3DialogSceneStrategy<T : Any> : SceneStrategy<T> {

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val lastEntry = entries.lastOrNull() ?: return null
    lastEntry.metadata[DialogKey] ?: return null

    @Suppress("UNCHECKED_CAST")
    return Nav3DialogScene(
      key = lastEntry.contentKey as T,
      previousEntries = entries.dropLast(1),
      overlaidEntries = entries.dropLast(1),
      entry = lastEntry,
      onBack = onBack,
    )
  }

  internal companion object {
    fun dialog() = metadata { put(DialogKey, true) }

    object DialogKey : NavMetadataKey<Boolean>
  }
}
