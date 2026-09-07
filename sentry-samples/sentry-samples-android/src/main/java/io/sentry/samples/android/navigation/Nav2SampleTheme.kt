package io.sentry.samples.android.navigation

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
internal fun Nav2SampleTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val primaryColor = Color(ContextCompat.getColor(context, R.color.colorPrimary))
  val accentColor = Color(ContextCompat.getColor(context, R.color.colorAccent))
  val colorScheme =
    if (isSystemInDarkTheme()) {
      darkColorScheme(primary = primaryColor, secondary = accentColor, tertiary = primaryColor)
    } else {
      lightColorScheme(primary = primaryColor, secondary = accentColor, tertiary = primaryColor)
    }

  MaterialTheme(colorScheme = colorScheme, content = content)
}
