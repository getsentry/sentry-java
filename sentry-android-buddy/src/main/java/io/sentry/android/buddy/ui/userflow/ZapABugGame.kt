package io.sentry.android.buddy.ui.userflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.ui.common.Icons
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyCode
import io.sentry.android.buddy.ui.common.theme.BuddyGold
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val GRID_SIDE = 3
private const val HOLE_COUNT = GRID_SIDE * GRID_SIDE
private const val MAX_BUGS = 3
private const val TICK_MS = 80L
private const val ZAP_FLASH_MS = 200L
private const val SPAWN_INTERVAL_START_MS = 900L
private const val SPAWN_INTERVAL_MIN_MS = 450L
private const val BUG_LIFE_START_MS = 1400L
private const val BUG_LIFE_MIN_MS = 900L

/** The score at which the game is as fast as it gets. */
private const val RAMP_SCORE = 20

internal sealed interface ZapHole {
  object Empty : ZapHole

  data class Bug(val spawnedAtMs: Long) : ZapHole

  data class Zapped(val atMs: Long) : ZapHole
}

/**
 * The whole game. [advance] moves it forward in time, [zap] answers a tap - both are pure, so the
 * board only ever changes on the main thread that drives the composable.
 */
internal data class ZapBoard(
  val holes: List<ZapHole> = List(HOLE_COUNT) { ZapHole.Empty },
  val score: Int = 0,
  val nextSpawnAtMs: Long = 0L,
) {
  fun advance(nowMs: Long, random: Random): ZapBoard {
    val bugLifeMs = ramped(BUG_LIFE_START_MS, BUG_LIFE_MIN_MS)
    var next = holes.map { hole ->
      when (hole) {
        is ZapHole.Bug -> if (nowMs - hole.spawnedAtMs >= bugLifeMs) ZapHole.Empty else hole
        is ZapHole.Zapped -> if (nowMs - hole.atMs >= ZAP_FLASH_MS) ZapHole.Empty else hole
        ZapHole.Empty -> hole
      }
    }
    if (nowMs < nextSpawnAtMs) {
      return copy(holes = next)
    }
    val free = next.indices.filter { next[it] == ZapHole.Empty }
    if (free.isNotEmpty() && next.count { it is ZapHole.Bug } < MAX_BUGS) {
      next = next.toMutableList().also { it[free.random(random)] = ZapHole.Bug(nowMs) }
    }
    return copy(
      holes = next,
      nextSpawnAtMs = nowMs + ramped(SPAWN_INTERVAL_START_MS, SPAWN_INTERVAL_MIN_MS),
    )
  }

  fun zap(index: Int, nowMs: Long): ZapBoard {
    if (holes[index] !is ZapHole.Bug) {
      return this
    }
    return copy(
      holes = holes.toMutableList().also { it[index] = ZapHole.Zapped(nowMs) },
      score = score + 1,
    )
  }

  /** Moves from [start] to [end] as the score climbs towards [RAMP_SCORE]. */
  private fun ramped(start: Long, end: Long): Long {
    val progress = (score.toFloat() / RAMP_SCORE).coerceIn(0f, 1f)
    return start - ((start - end) * progress).toLong()
  }
}

/** A small "zap a bug" game, to pass the time while Seer thinks. */
@Composable
internal fun BuddyZapABug(modifier: Modifier = Modifier) {
  var board by remember { mutableStateOf(ZapBoard()) }
  var elapsedMs by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) {
    val random = Random.Default
    while (true) {
      delay(TICK_MS)
      elapsedMs += TICK_MS
      board = board.advance(elapsedMs, random)
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(
      modifier = Modifier.widthIn(max = 240.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (row in 0 until GRID_SIDE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (column in 0 until GRID_SIDE) {
            val index = row * GRID_SIDE + column
            ZapHoleCell(
              hole = board.holes[index],
              modifier = Modifier.weight(1f).aspectRatio(1f),
              onZap = { board = board.zap(index, elapsedMs) },
            )
          }
        }
      }
    }
    Row(
      modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Zapped: ${board.score}",
        color = BuddyMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(onClick = { board = ZapBoard(nextSpawnAtMs = elapsedMs) }) { Text("Reset") }
    }
  }
}

@Composable
private fun ZapHoleCell(hole: ZapHole, modifier: Modifier = Modifier, onZap: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(BuddyCode)
        .border(1.dp, BuddyBorder, shape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onZap,
        ),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedVisibility(visible = hole is ZapHole.Bug, enter = scaleIn(), exit = scaleOut()) {
      Icon(
        imageVector = Icons.bug,
        contentDescription = "Zap the bug",
        tint = BuddyInk,
        modifier = Modifier.size(36.dp),
      )
    }
    AnimatedVisibility(visible = hole is ZapHole.Zapped, enter = fadeIn(), exit = fadeOut()) {
      Icon(
        imageVector = Icons.bolt,
        contentDescription = null,
        tint = BuddyGold,
        modifier = Modifier.size(36.dp),
      )
    }
  }
}

@Preview(name = "Zap a bug", showBackground = true, widthDp = 380)
@Composable
private fun BuddyZapABugPreview() {
  BuddyPreviewSurface { BuddyZapABug(modifier = Modifier.padding(16.dp)) }
}
