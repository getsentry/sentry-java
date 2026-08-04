/*
 * Adapted from https://github.com/android/nav3-recipes/blob/main/app/src/main/java/com/example/nav3recipes/bottomsheet/BottomSheetSceneStrategy.kt
 * Copyright (C) 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package io.sentry.samples.android.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/** Displays marked Nav3 entries in a [ModalBottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
internal data class Nav3BottomSheetScene<T : Any>(
  override val key: T,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val properties: ModalBottomSheetProperties,
  private val onBack: () -> Unit,
) : OverlayScene<T> {

  override val entries: List<NavEntry<T>> = listOf(entry)

  override val content: @Composable (() -> Unit) = {
    ModalBottomSheet(onDismissRequest = onBack, properties = properties) { entry.Content() }
  }
}

/** Displays entries with [bottomSheet] metadata as bottom-sheet overlays. */
@OptIn(ExperimentalMaterial3Api::class)
internal class Nav3BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val lastEntry = entries.lastOrNull() ?: return null
    val properties = lastEntry.metadata[BottomSheetKey] ?: return null

    @Suppress("UNCHECKED_CAST")
    return Nav3BottomSheetScene(
      key = lastEntry.contentKey as T,
      previousEntries = entries.dropLast(1),
      overlaidEntries = entries.dropLast(1),
      entry = lastEntry,
      properties = properties,
      onBack = onBack,
    )
  }

  internal companion object {
    fun bottomSheet(properties: ModalBottomSheetProperties = ModalBottomSheetProperties()) =
      metadata {
        put(BottomSheetKey, properties)
      }

    object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>
  }
}
