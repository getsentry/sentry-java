package io.sentry.samples.android.navigation.nav3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.sentry.samples.android.R

@Composable
internal fun Nav3SampleTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val primaryColor = Color(ContextCompat.getColor(context, R.color.colorPrimary))
  val accentColor = Color(ContextCompat.getColor(context, R.color.colorAccent))
  val darkBackground = Color(0xFF333333)
  val darkSurface = Color(0xFF333333)
  val darkSurfaceVariant = Color(0xFF454545)
  val darkSurfaceContainer = Color(0xFF66666A)
  val darkOnBackground = Color(0xFFF2ECF7)
  val darkOnSurface = Color(0xFFF2ECF7)
  val darkOnSurfaceVariant = Color(0xFFD0CAD6)
  val lightBackground = Color(0xFFF6F1F8)
  val lightSurface = Color(0xFFF6F1F8)
  val lightSurfaceVariant = Color(0xFFE4DEEA)
  val lightOnBackground = Color(0xFF241F29)
  val lightOnSurface = Color(0xFF241F29)
  val lightOnSurfaceVariant = Color(0xFF5F5868)
  val colorScheme =
    if (isSystemInDarkTheme()) {
      darkColorScheme(
        primary = primaryColor,
        secondary = accentColor,
        tertiary = primaryColor,
        background = darkBackground,
        surface = darkSurface,
        surfaceVariant = darkSurfaceVariant,
        surfaceContainer = darkSurfaceContainer,
        onBackground = darkOnBackground,
        onSurface = darkOnSurface,
        onSurfaceVariant = darkOnSurfaceVariant,
      )
    } else {
      lightColorScheme(
        primary = primaryColor,
        secondary = accentColor,
        tertiary = primaryColor,
        background = lightBackground,
        surface = lightSurface,
        surfaceVariant = lightSurfaceVariant,
        onBackground = lightOnBackground,
        onSurface = lightOnSurface,
        onSurfaceVariant = lightOnSurfaceVariant,
      )
    }

  MaterialTheme(colorScheme = colorScheme, content = content)
}
