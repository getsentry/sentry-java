package io.sentry.samples.android

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class ReplayAnimationsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val primaryColor = Color(ContextCompat.getColor(this, R.color.colorPrimary))
      val accentColor = Color(ContextCompat.getColor(this, R.color.colorAccent))
      val colorScheme =
        if (isSystemInDarkTheme())
          darkColorScheme(primary = primaryColor, secondary = accentColor, tertiary = primaryColor)
        else
          lightColorScheme(primary = primaryColor, secondary = accentColor, tertiary = primaryColor)

      MaterialTheme(colorScheme = colorScheme) { ReplayAnimationsScreen(onClose = { finish() }) }
    }
  }
}

@Composable
private fun ReplayAnimationsScreen(onClose: () -> Unit) {
  var selectedSample by remember { mutableStateOf<ReplayAnimationSample?>(null) }

  BackHandler(enabled = selectedSample != null) { selectedSample = null }

  selectedSample?.let { sample ->
    ReplayAnimationDetailScreen(sample = sample, onBack = { selectedSample = null })
    return
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    Text(
      text = "Replay animations",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )
    ReplayAnimationSample.entries.forEach { sample ->
      Button(onClick = { selectedSample = sample }, modifier = Modifier.fillMaxWidth()) {
        Text(sample.title)
      }
    }
  }
}

@Composable
private fun ReplayAnimationDetailScreen(sample: ReplayAnimationSample, onBack: () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Button(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text("Back") }
    Text(
      text = sample.title,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Surface(
      modifier = Modifier.fillMaxWidth().height(420.dp),
      shape = RoundedCornerShape(8.dp),
      tonalElevation = 2.dp,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        when (sample) {
          ReplayAnimationSample.LOTTIE -> LottieReplayAnimation()
          ReplayAnimationSample.COMPOSE_CANVAS -> ComposeCanvasAnimation()
          ReplayAnimationSample.ANDROID_VIEWS ->
            AndroidView(
              factory = { context -> ClassicAnimationLayout(context) },
              modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }
      }
    }
  }
}

private enum class ReplayAnimationSample(val title: String) {
  LOTTIE("Lottie"),
  COMPOSE_CANVAS("Compose canvas"),
  ANDROID_VIEWS("Android views"),
}

@Composable
private fun LottieReplayAnimation() {
  val composition by
    rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.replay_lottie_pulse))
  val progress by
    animateLottieCompositionAsState(
      composition = composition,
      iterations = LottieConstants.IterateForever,
    )

  LottieAnimation(
    composition = composition,
    progress = { progress },
    modifier = Modifier.fillMaxSize(),
  )
}

@Composable
private fun ComposeCanvasAnimation() {
  val transition = rememberInfiniteTransition()
  val angle by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
    )
  val pulse by
    transition.animateFloat(
      initialValue = 0.25f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(900, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
    )

  Canvas(
    modifier =
      Modifier.fillMaxWidth().height(160.dp).background(Color(0xFF101820), RoundedCornerShape(8.dp))
  ) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val orbitRadius = min(size.width, size.height) * 0.32f
    val ballRadius = min(size.width, size.height) * 0.1f
    val radians = angle / 180f * PI.toFloat()

    drawCircle(
      color = Color(0xFF8BE9FD),
      radius = orbitRadius * pulse,
      center = center,
      style = Stroke(width = 5.dp.toPx()),
      alpha = 0.55f,
    )
    drawCircle(
      color = Color(0xFFFF6B6B),
      radius = ballRadius,
      center = Offset(center.x + cos(radians) * orbitRadius, center.y + sin(radians) * orbitRadius),
    )
    drawCircle(
      color = Color(0xFFFFD166),
      radius = ballRadius * 0.75f,
      center =
        Offset(
          center.x + cos(radians + PI.toFloat()) * orbitRadius,
          center.y + sin(radians + PI.toFloat()) * orbitRadius,
        ),
    )
  }
}

private class ClassicAnimationLayout(context: Context) : FrameLayout(context) {
  private val movingDot =
    View(context).apply { background = ovalDrawable(AndroidColor.rgb(255, 107, 107)) }
  private val rotatingSquare =
    View(context).apply { background = roundedRectDrawable(AndroidColor.rgb(139, 233, 253), dp(8)) }
  private val scalingBar =
    View(context).apply { background = roundedRectDrawable(AndroidColor.rgb(255, 209, 102), dp(6)) }
  private val animators: List<Animator>

  init {
    setBackgroundColor(AndroidColor.rgb(16, 24, 32))
    clipChildren = false
    clipToPadding = false

    addView(scalingBar, LayoutParams(dp(180), dp(18), Gravity.CENTER).apply { topMargin = dp(116) })
    addView(rotatingSquare, LayoutParams(dp(64), dp(64), Gravity.CENTER))
    addView(movingDot, LayoutParams(dp(48), dp(48), Gravity.CENTER))

    animators =
      listOf(
        ObjectAnimator.ofFloat(movingDot, View.TRANSLATION_X, -dp(92).toFloat(), dp(92).toFloat())
          .repeatable(durationMillis = 900, mode = ValueAnimator.REVERSE),
        ObjectAnimator.ofFloat(movingDot, View.TRANSLATION_Y, -dp(28).toFloat(), dp(28).toFloat())
          .repeatable(durationMillis = 650, mode = ValueAnimator.REVERSE),
        ObjectAnimator.ofFloat(rotatingSquare, View.ROTATION, 0f, 360f)
          .repeatable(durationMillis = 1200),
        ObjectAnimator.ofFloat(scalingBar, View.SCALE_X, 0.25f, 1f)
          .repeatable(durationMillis = 800, mode = ValueAnimator.REVERSE),
      )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    animators.forEach { animator ->
      if (!animator.isStarted) {
        animator.start()
      }
    }
  }

  override fun onDetachedFromWindow() {
    animators.forEach { it.cancel() }
    super.onDetachedFromWindow()
  }

  private fun ObjectAnimator.repeatable(
    durationMillis: Long,
    mode: Int = ValueAnimator.RESTART,
  ): ObjectAnimator = apply {
    duration = durationMillis
    interpolator = LinearInterpolator()
    repeatCount = ValueAnimator.INFINITE
    repeatMode = mode
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

  private fun ovalDrawable(color: Int): GradientDrawable =
    GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(color)
    }

  private fun roundedRectDrawable(color: Int, radius: Int): GradientDrawable =
    GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = radius.toFloat()
      setColor(color)
    }
}
