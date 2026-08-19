package io.sentry.android.buddy.ui.common.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.ui.common.Icons
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyGold
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewTimelineRows

private val LINK_INDICATOR_SIZE = 16.dp

/**
 * The single timeline renderer. Every Buddy surface that shows a trace - the live feed, the
 * attention card and the stopped-recording summary - feeds [BuddyTimelineRow]s into this one
 * composable so they all read the same way.
 */
@Composable
internal fun BuddyTimeline(
  rows: List<BuddyTimelineRow>,
  modifier: Modifier = Modifier,
  showOverflowEllipsis: Boolean = false,
  onRowClick: ((BuddyTimelineRow) -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = Color.Transparent,
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column {
      rows.forEachIndexed { index, row ->
        val clickable = onRowClick != null && row.link != null
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable(enabled = clickable) { onRowClick?.invoke(row) }
              .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            row.annotate(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
          )
          if (clickable) {
            Icon(
              imageVector = Icons.open_in_new,
              contentDescription = stringResource(R.string.buddy_timeline_row_opens_link),
              modifier = Modifier.padding(start = 8.dp).size(LINK_INDICATOR_SIZE),
              tint = BuddyMuted,
            )
          }
        }
        if (index != rows.lastIndex || showOverflowEllipsis) {
          HorizontalDivider(color = BuddyBorder)
        }
      }
      if (showOverflowEllipsis) {
        Text(
          "…",
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          color = BuddyMuted,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}

/** Elapsed stamp stays muted, the rest of the line carries the tone colour. */
@Composable
private fun BuddyTimelineRow.annotate(): AnnotatedString = buildAnnotatedString {
  withStyle(SpanStyle(color = BuddyMuted)) { append(stamp) }
  append(" ")
  withStyle(
    SpanStyle(
      color = tone.color(),
      fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
    )
  ) {
    if (detail.isNotBlank()) {
      append(' ')
      append(detail)
    }
    trailing
      ?.takeIf { it.isNotBlank() }
      ?.let {
        append(' ')
        append(it)
      }
  }
}

private fun BuddyTimelineTone.color(): Color =
  when (this) {
    BuddyTimelineTone.NEUTRAL -> BuddyInk
    BuddyTimelineTone.ACCENT -> BuddyPurple
    BuddyTimelineTone.WARNING -> BuddyGold
    BuddyTimelineTone.ERROR -> BuddyRed
  }

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun BuddyTimelinePreview() {
  BuddyPreviewSurface { BuddyTimeline(previewTimelineRows, onRowClick = {}) }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun BuddyTimelineOverflowPreview() {
  BuddyPreviewSurface {
    BuddyTimeline(previewTimelineRows.take(3), showOverflowEllipsis = true)
  }
}
