package io.sentry.android.buddy.ui.userflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.ui.common.BuddyWavyProgress
import io.sentry.android.buddy.ui.common.Icons
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleEnd
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleShadow
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleStart
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyCode
import io.sentry.android.buddy.ui.common.theme.BuddyGold
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.common.theme.BuddyWarningBubbleEnd
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
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
private const val PUPIL_IDLE_ORBIT_MS = 1800
private const val LASER_CORE_FRACTION = 0.45f
private const val PUPIL_X_TRAVEL_FRACTION = 0.11f
private const val PUPIL_Y_TRAVEL_FRACTION = 0.08f
private const val PUPIL_BASELINE_Y_FRACTION = 0.05f
private const val LASER_ORIGIN_Y_FRACTION = 0.07f
private const val LASER_ORIGIN_X_FRACTION = 0.12f
private const val TAU = (2.0 * Math.PI).toFloat()

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
  val hits: Int = 0,
  val misses: Int = 0,
  val nextSpawnAtMs: Long = 0L,
  val lastZapAtMs: Long? = null,
  val lastZapIndex: Int? = null,
) {
  fun advance(nowMs: Long, random: Random): ZapBoard {
    val bugLifeMs = ramped(BUG_LIFE_START_MS, BUG_LIFE_MIN_MS)
    var missedBugs = 0
    var next = holes.map { hole ->
      when (hole) {
        is ZapHole.Bug ->
          if (nowMs - hole.spawnedAtMs >= bugLifeMs) {
            missedBugs += 1
            ZapHole.Empty
          } else {
            hole
          }
        is ZapHole.Zapped -> if (nowMs - hole.atMs >= ZAP_FLASH_MS) ZapHole.Empty else hole
        ZapHole.Empty -> hole
      }
    }
    val zapStillVisible = lastZapAtMs?.let { nowMs - it < ZAP_FLASH_MS } == true
    if (nowMs < nextSpawnAtMs) {
      return copy(
        holes = next,
        misses = misses + missedBugs,
        lastZapAtMs = if (zapStillVisible) lastZapAtMs else null,
        lastZapIndex = if (zapStillVisible) lastZapIndex else null,
      )
    }
    val free = next.indices.filter { next[it] == ZapHole.Empty }
    if (free.isNotEmpty() && next.count { it is ZapHole.Bug } < MAX_BUGS) {
      next = next.toMutableList().also { it[free.random(random)] = ZapHole.Bug(nowMs) }
    }
    return copy(
      holes = next,
      misses = misses + missedBugs,
      nextSpawnAtMs = nowMs + ramped(SPAWN_INTERVAL_START_MS, SPAWN_INTERVAL_MIN_MS),
      lastZapAtMs = if (zapStillVisible) lastZapAtMs else null,
      lastZapIndex = if (zapStillVisible) lastZapIndex else null,
    )
  }

  fun zap(index: Int, nowMs: Long): ZapBoard {
    if (holes[index] !is ZapHole.Bug) {
      return this
    }
    return copy(
      holes = holes.toMutableList().also { it[index] = ZapHole.Zapped(nowMs) },
      hits = hits + 1,
      lastZapAtMs = nowMs,
      lastZapIndex = index,
    )
  }

  /** Moves from [start] to [end] as the score climbs towards [RAMP_SCORE]. */
  private fun ramped(start: Long, end: Long): Long {
    val progress = (hits.toFloat() / RAMP_SCORE).coerceIn(0f, 1f)
    return start - ((start - end) * progress).toLong()
  }
}

/** A small "zap a bug" game, to pass the time while Seer thinks. */
@Composable
internal fun BuddyZapABug(modifier: Modifier = Modifier) {
  var board by remember { mutableStateOf(ZapBoard()) }
  var elapsedMs by remember { mutableLongStateOf(0L) }
  val density = LocalDensity.current
  val seerSizePx = with(density) { 88.dp.toPx() }
  var arenaOriginInRoot by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
  var seerCenterInRoot by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
  val holeCentersInRoot = remember { mutableStateMapOf<Int, androidx.compose.ui.geometry.Offset>() }
  LaunchedEffect(Unit) {
    val random = Random.Default
    while (true) {
      delay(TICK_MS)
      elapsedMs += TICK_MS
      board = board.advance(elapsedMs, random)
    }
  }

  val zapProgress =
    board.lastZapAtMs?.let {
      ((elapsedMs - it).coerceAtLeast(0L).toFloat() / ZAP_FLASH_MS).coerceIn(0f, 1f)
    }
  val seerCenterInArena = arenaOriginInRoot?.let { arenaOrigin ->
    seerCenterInRoot?.minus(arenaOrigin)
  }
  val targetCenterInArena =
    board.lastZapIndex?.let(holeCentersInRoot::get)?.let { targetCenter ->
      arenaOriginInRoot?.let { arenaOrigin -> targetCenter - arenaOrigin }
    }
  val lookVector =
    if (seerCenterInArena != null && targetCenterInArena != null) {
      targetCenterInArena - seerCenterInArena
    } else {
      null
    }

  Box(
    modifier =
      modifier.fillMaxWidth().onGloballyPositioned { arenaOriginInRoot = it.positionInRoot() },
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BuddyAnalyzingSeer(
        modifier =
          Modifier.offset(y = (-16).dp).onGloballyPositioned { coordinates ->
            seerCenterInRoot = coordinates.centerInRoot()
          },
        lookVector = lookVector,
      )
      Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontStyle = FontStyle.Italic,
        text = "Hold tight. Seer is having a look at your flow.",
      )
      BuddyWavyProgress(modifier = Modifier.padding(horizontal = 16.dp), color = BuddyRed)
      Text(
        text = "Zap some bugs while you wait.",
        color = BuddyMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
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
                modifier =
                  Modifier.weight(1f).aspectRatio(1f).onGloballyPositioned { coordinates ->
                    holeCentersInRoot[index] = coordinates.centerInRoot()
                  },
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Hit: ${board.hits}",
            color = BuddyMuted,
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = "Missed: ${board.misses}",
            color = BuddyMuted,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        TextButton(
          onClick = {
            board = ZapBoard(nextSpawnAtMs = elapsedMs)
            holeCentersInRoot.clear()
          }
        ) {
          Text("Reset")
        }
      }
    }
    if (zapProgress != null && seerCenterInArena != null && targetCenterInArena != null) {
      BuddySeerLaser(
        modifier = Modifier.matchParentSize(),
        start = seerCenterInArena,
        end = targetCenterInArena,
        progress = zapProgress,
        seerSizePx = seerSizePx,
      )
    }
  }
}

@Composable
private fun BuddyAnalyzingSeer(
  modifier: Modifier = Modifier,
  lookVector: androidx.compose.ui.geometry.Offset?,
  size: Dp = 88.dp,
) {
  val density = LocalDensity.current
  val sizePx = with(density) { size.toPx() }
  val pupilSize = size * 0.17f
  val idleOrbit by
    androidx.compose.animation.core
      .rememberInfiniteTransition(label = "seer-icon")
      .animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec =
          androidx.compose.animation.core.infiniteRepeatable(
            tween(PUPIL_IDLE_ORBIT_MS, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Restart,
          ),
        label = "seer-pupil-orbit",
      )
  val idleTarget =
    androidx.compose.ui.geometry.Offset(
      x = (cos(idleOrbit) * 0.85f).toFloat(),
      y = (sin(idleOrbit * 1.3f) * 0.7f).toFloat(),
    )
  val focusTarget = lookVector?.normalized()
  val desiredTarget = focusTarget ?: idleTarget
  val pupilX by
    animateFloatAsState(
      targetValue = desiredTarget.x,
      animationSpec = tween(durationMillis = if (focusTarget != null) 120 else 320),
      label = "seer-pupil-x",
    )
  val pupilY by
    animateFloatAsState(
      targetValue = desiredTarget.y,
      animationSpec = tween(durationMillis = if (focusTarget != null) 120 else 320),
      label = "seer-pupil-y",
    )
  val bubbleBrush = remember {
    Brush.linearGradient(
      colors = listOf(BuddyAccentBubbleStart, BuddyAccentBubbleEnd),
      start = androidx.compose.ui.geometry.Offset(0f, 0f),
      end = androidx.compose.ui.geometry.Offset(1f, 1f),
    )
  }

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.size(size * 0.84f)
          .shadow(14.dp, CircleShape)
          .background(BuddyAccentBubbleShadow.copy(alpha = 0.45f), CircleShape)
    )
    Box(
      modifier = Modifier.size(size * 0.8f).background(bubbleBrush, CircleShape).clip(CircleShape)
    )
    Image(
      painter = painterResource(R.drawable.ic_buddy_eye_shell),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
    )
    Box(
      modifier =
        Modifier.size(pupilSize)
          .offset {
            IntOffset(
              x = (pupilX * sizePx * PUPIL_X_TRAVEL_FRACTION).roundToInt(),
              y =
                (sizePx * PUPIL_BASELINE_Y_FRACTION + pupilY * sizePx * PUPIL_Y_TRAVEL_FRACTION)
                  .roundToInt(),
            )
          }
          .background(Color.White, CircleShape)
    )
  }
}

@Composable
private fun BuddySeerLaser(
  start: androidx.compose.ui.geometry.Offset,
  end: androidx.compose.ui.geometry.Offset,
  progress: Float,
  seerSizePx: Float,
  modifier: Modifier = Modifier,
) {
  val direction = (end - start).normalized()
  val alpha = (1f - progress).coerceIn(0f, 1f)
  val laserStart =
    start +
      androidx.compose.ui.geometry.Offset(
        x = direction.x * LASER_ORIGIN_X_FRACTION * seerSizePx,
        y =
          direction.y * LASER_ORIGIN_X_FRACTION * seerSizePx + LASER_ORIGIN_Y_FRACTION * seerSizePx,
      )
  Canvas(modifier = modifier) {
    val glowStroke = size.minDimension * 0.03f * alpha.coerceAtLeast(0.5f)
    val coreStroke = glowStroke * LASER_CORE_FRACTION
    drawLine(
      color = BuddyWarningBubbleEnd.copy(alpha = 0.35f * alpha),
      start = laserStart,
      end = end,
      strokeWidth = glowStroke,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = Color.White.copy(alpha = alpha),
      start = laserStart,
      end = end,
      strokeWidth = coreStroke,
      cap = StrokeCap.Round,
    )
    drawCircle(color = BuddyWarningBubbleEnd.copy(alpha = alpha), radius = glowStroke, center = end)
  }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.centerInRoot():
  androidx.compose.ui.geometry.Offset {
  val position = positionInRoot()
  return position + androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
}

private fun androidx.compose.ui.geometry.Offset.normalized(): androidx.compose.ui.geometry.Offset {
  val distance = hypot(x.toDouble(), y.toDouble()).toFloat()
  if (distance <= 0.001f) {
    return androidx.compose.ui.geometry.Offset.Zero
  }
  return androidx.compose.ui.geometry.Offset(x / distance, y / distance)
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
