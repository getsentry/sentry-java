package io.sentry.samples.android.sqlite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.sentry.samples.android.R

private val ShimmerHighlight = Color(0xFFBDBDBD)

@Composable
fun UiLoadScreen(status: String, onClose: () -> Unit) {
  MaterialTheme {
    Surface {
      Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)
      ) {
        Column(
          modifier = Modifier.align(Alignment.Center).fillMaxWidth().offset(y = (-48).dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          ShimmerSentryGlyph(modifier = Modifier.size(96.dp))
          Spacer(modifier = Modifier.height(24.dp))
          Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
        }

        Button(
          onClick = onClose,
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
          colors =
            ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        ) {
          Text("Close")
        }
      }
    }
  }
}

@Composable
private fun ShimmerSentryGlyph(modifier: Modifier = Modifier) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 700, delayMillis = 250, easing = LinearEasing),
    )
  }

  Image(
    painter = painterResource(R.drawable.sentry_glyph),
    contentDescription = "Sentry",
    modifier =
      modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          val p = progress.value
          val band = size.width * 0.5f
          // Sweep the highlight band diagonally from off the bottom-left corner (p=0) to off the
          // top-right corner (p=1): x travels left→right, y travels bottom→top.
          val x = -band + (size.width + 2f * band) * p
          val y = (size.height + band) - (size.height + 2f * band) * p
          drawRect(
            brush =
              Brush.linearGradient(
                colors = listOf(Color.Black, ShimmerHighlight, Color.Black),
                start = Offset(x, y),
                end = Offset(x + band, y - band),
              ),
            blendMode = BlendMode.SrcAtop,
          )
        },
  )
}
