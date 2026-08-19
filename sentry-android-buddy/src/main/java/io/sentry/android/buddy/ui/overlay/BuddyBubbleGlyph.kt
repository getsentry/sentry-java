package io.sentry.android.buddy.ui.overlay

import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import io.sentry.android.buddy.R
import io.sentry.android.buddy.model.BuddyScreenScanState
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleGlyphSize
import io.sentry.android.buddy.ui.common.theme.BuddyScanElectricCore
import io.sentry.android.buddy.ui.common.theme.BuddyScanElectricGlow

internal enum class BuddyBubbleGlyphState {
  IDLE,
  UNREAD,
  ANALYZING,
  INSIGHTS_READY,
  SEVERE,
  RECORDING,
}

@Composable
internal fun BuddyBubbleAnimatedDrawable(drawableRes: Int, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  AndroidView(
    factory = { viewContext ->
      AppCompatImageView(viewContext).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
      }
    },
    modifier = modifier,
    update = { imageView -> imageView.bindBuddyDrawable(context, drawableRes) },
  )
}

@Composable
internal fun BuddyBubbleGlyph(state: BuddyBubbleGlyphState) {
  val context = LocalContext.current
  Box(
    modifier = Modifier.size(BuddyBubbleGlyphSize).clip(CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { viewContext ->
        AppCompatImageView(viewContext).apply {
          scaleType = ImageView.ScaleType.FIT_CENTER
          importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
      },
      modifier = Modifier.fillMaxSize(),
      update = { imageView -> imageView.bindBuddyBubbleGlyph(context, state) },
    )
  }
}

@Composable
internal fun BubbleNotificationBadge(count: String, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier.size(22.dp).background(color, CircleShape).border(2.dp, Color.White, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = count,
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
internal fun ScreenScanElectricityOverlay(screenScanState: BuddyScreenScanState) {
  val scanningState = screenScanState as? BuddyScreenScanState.Scanning ?: return
  val rootBounds = scanningState.result.bounds.maxByOrNull { it.width * it.height } ?: return
  val transition = rememberInfiniteTransition(label = "buddy-screen-scan-electricity")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 520),
          repeatMode = RepeatMode.Restart,
        ),
      label = "buddy-screen-scan-electricity-phase",
    )
  Canvas(
    modifier =
      Modifier.fillMaxSize().graphicsLayer { alpha = 1f - (phase * 0.18f).coerceIn(0f, 0.18f) }
  ) {
    val inset = 8f
    val pulse = phase
    val glowAlpha = (0.26f + pulse * 0.22f).coerceIn(0.26f, 0.48f)
    val topLeft = Offset(rootBounds.left + inset, rootBounds.top + inset)
    val size =
      Size(
        (rootBounds.width - inset * 2).coerceAtLeast(1f),
        (rootBounds.height - inset * 2).coerceAtLeast(1f),
      )
    val cornerRadius = CornerRadius(28f, 28f)
    drawRoundRect(
      color = BuddyScanElectricGlow.copy(alpha = glowAlpha),
      topLeft = topLeft,
      size = size,
      cornerRadius = cornerRadius,
      style =
        androidx.compose.ui.graphics.drawscope.Stroke(
          width = 16f,
          pathEffect =
            PathEffect.dashPathEffect(
              floatArrayOf(64f, 260f, 28f, 190f, 44f, 320f),
              phase * 520f,
            ),
        ),
    )
    drawRoundRect(
      color = BuddyScanElectricCore.copy(alpha = 0.92f),
      topLeft = topLeft,
      size = size,
      cornerRadius = cornerRadius,
      style =
        androidx.compose.ui.graphics.drawscope.Stroke(
          width = 5f,
          pathEffect =
            PathEffect.dashPathEffect(
              floatArrayOf(36f, 240f, 18f, 180f, 26f, 300f),
              phase * 560f,
            ),
        ),
    )
    drawRoundRect(
      color = Color(0xFFFFF3B0).copy(alpha = 0.72f),
      topLeft = topLeft,
      size = size,
      cornerRadius = cornerRadius,
      style =
        androidx.compose.ui.graphics.drawscope.Stroke(
          width = 3f,
          pathEffect =
            PathEffect.dashPathEffect(
              floatArrayOf(10f, 280f, 14f, 220f, 8f, 340f),
              phase * -620f,
            ),
        ),
    )
  }
}

internal fun ImageView.bindBuddyBubbleGlyph(context: Context, state: BuddyBubbleGlyphState) {
  val drawableRes =
    when (state) {
      BuddyBubbleGlyphState.IDLE -> R.drawable.avd_buddy_idle
      BuddyBubbleGlyphState.UNREAD -> R.drawable.avd_buddy_unread
      BuddyBubbleGlyphState.ANALYZING -> R.drawable.avd_buddy_analyzing
      BuddyBubbleGlyphState.INSIGHTS_READY -> R.drawable.avd_buddy_ready
      BuddyBubbleGlyphState.SEVERE -> R.drawable.avd_buddy_severe
      BuddyBubbleGlyphState.RECORDING -> R.drawable.ic_buddy_recording
    }
  bindBuddyDrawable(context, drawableRes, loopIdle = state == BuddyBubbleGlyphState.IDLE)
}

internal fun ImageView.bindBuddyDrawable(
  context: Context,
  drawableRes: Int,
  loopIdle: Boolean = false,
) {
  val currentTag = tag as? Int
  if (currentTag == drawableRes) {
    return
  }
  tag = drawableRes
  val nextDrawable = AppCompatResources.getDrawable(context, drawableRes)?.mutate()
  setImageDrawable(nextDrawable)
  restartBuddyBubbleAnimation(nextDrawable, loopIdle = loopIdle)
}

internal fun restartBuddyBubbleAnimation(drawable: Drawable?, loopIdle: Boolean) {
  when (drawable) {
    is AnimatedVectorDrawableCompat -> drawable.restart(loopIdle = loopIdle)
    is AnimatedVectorDrawable -> drawable.restart(loopIdle = loopIdle)
  }
}

internal fun AnimatedVectorDrawableCompat.restart(loopIdle: Boolean) {
  clearAnimationCallbacks()
  if (loopIdle) {
    registerAnimationCallback(
      object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
          start()
        }
      }
    )
  }
  stop()
  start()
}

internal fun AnimatedVectorDrawable.restart(loopIdle: Boolean) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    clearAnimationCallbacks()
    if (loopIdle) {
      registerAnimationCallback(
        object : Animatable2.AnimationCallback() {
          override fun onAnimationEnd(drawable: Drawable?) {
            start()
          }
        }
      )
    }
  }
  stop()
  start()
}
