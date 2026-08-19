package io.sentry.android.buddy.ui.common

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import io.sentry.android.buddy.R

@Composable
internal fun SentryBuddyGlyph(tint: Color, modifier: Modifier = Modifier) {
  Icon(
    painter = painterResource(id = R.drawable.sentry_buddy_glyph_light),
    contentDescription = null,
    modifier = modifier,
    tint = tint,
  )
}
