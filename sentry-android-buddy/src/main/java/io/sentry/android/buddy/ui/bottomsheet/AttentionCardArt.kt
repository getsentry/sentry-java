package io.sentry.android.buddy.ui.bottomsheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.EMPTY_ATTENTION_ART_VARIANTS

internal data class PerformanceStat(val value: String, val label: String)

@Composable
internal fun PerformanceHeroStatCard(stat: PerformanceStat, color: Color) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.White.copy(alpha = 0.88f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        stat.value,
        color = color,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
      Text(
        stat.label,
        color = BuddyMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
internal fun PerformanceContextCards(item: BuddyLiveFeedItem, color: Color) {
  val contextCards =
    listOfNotNull(
      item.performanceSourceLabel()?.let { PerformanceStat(it, "Source") },
      item.visibleScreens.lastOrNull()?.let { PerformanceStat(it, "Screen") },
    )
  if (contextCards.isEmpty()) {
    return
  }

  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    contextCards.forEach { stat ->
      Surface(
        modifier = Modifier.weight(1f),
        color = Color.White.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            stat.label,
            color = BuddyMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            stat.value,
            color = if (stat.label == "Source") color else BuddyInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
internal fun EmptyAttentionArt(index: Int, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(id = emptyAttentionArtResource(index)),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
  )
}

internal fun emptyAttentionArtResource(index: Int): Int =
  when (index % EMPTY_ATTENTION_ART_VARIANTS) {
    0 -> R.drawable.buddy_attention_android_anr
    1 -> R.drawable.buddy_attention_tombstone_support
    2 -> R.drawable.buddy_attention_ai_momentum
    3 -> R.drawable.buddy_attention_seer_helps
    4 -> R.drawable.buddy_attention_snapshot
    5 -> R.drawable.buddy_attention_nextjs_otel
    6 -> R.drawable.buddy_attention_auth_doorway
    7 -> R.drawable.buddy_attention_black_friday
    8 -> R.drawable.buddy_attention_startups
    else -> R.drawable.buddy_attention_android_anr
  }

internal object EmptyAttentionArtIndex {
  private var nextIndex = 0

  fun next(): Int {
    val index = nextIndex
    nextIndex = (nextIndex + 1) % EMPTY_ATTENTION_ART_VARIANTS
    return index
  }
}

internal fun adverseCountChips(liveFeed: BuddyLiveFeed): List<String> {
  val adverseItems = liveFeed.items.filter { it.adverse }
  return listOfNotNull(
    adverseItems.count { it.category == BuddyLiveFeedItem.Category.ERROR }.positiveChip("errors"),
    adverseItems
      .count {
        it.category == BuddyLiveFeedItem.Category.SLOW_SPAN ||
          it.category == BuddyLiveFeedItem.Category.FAILED_SPAN
      }
      .positiveChip("spans"),
    adverseItems
      .count { it.category == BuddyLiveFeedItem.Category.FAILED_HTTP }
      .positiveChip("HTTP"),
  )
}

internal fun Int.positiveChip(label: String): String? = if (this > 0) "$this $label" else null

/** The picked issue in context: a few live feed rows around it, rendered by the shared timeline. */
@Composable
internal fun AttentionSourcePill(source: String) {
  Surface(color = Color.White.copy(alpha = 0.85f), shape = RoundedCornerShape(18.dp)) {
    Text(
      source,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      color = BuddyMuted,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
internal fun AttentionCountChips(liveFeed: BuddyLiveFeed) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    adverseCountChips(liveFeed).forEach { chip ->
      Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(),
      ) {
        Text(
          chip,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          color = BuddyInk,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Normal,
        )
      }
    }
  }
}
