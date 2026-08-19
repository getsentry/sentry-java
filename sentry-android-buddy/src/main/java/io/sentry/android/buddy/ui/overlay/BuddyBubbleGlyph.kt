package io.sentry.android.buddy.ui.overlay

import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import io.sentry.android.buddy.R
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleGlyphSize

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
internal fun BuddyBubbleGlyph(state: BuddyBubbleGlyphState, size: Dp = BuddyBubbleGlyphSize) {
  val context = LocalContext.current
  Box(
    modifier = Modifier.size(size).clip(CircleShape),
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
