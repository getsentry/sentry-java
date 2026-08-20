package io.sentry.android.buddy.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.model.ActionStatus
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.PerformanceCharacteristics
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyBridgeOrange
import io.sentry.android.buddy.ui.common.theme.BuddyCode
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyRecommendationRed
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.common.theme.BuddyReplayBlueHighlight
import io.sentry.android.buddy.ui.common.theme.EMPTY_ATTENTION_ART_VARIANTS
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.PREVIEW_NOW_MS
import io.sentry.android.buddy.ui.preview.previewHomeRecommendation
import io.sentry.android.buddy.ui.preview.previewRecommendation
import io.sentry.android.buddy.ui.preview.previewSpanRecommendation
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RECOMMENDATION_DISMISS_ANIMATION_MS = 220

/**
 * What a recommendation looks like once it is ready to draw. Flow analysis, the home list and the
 * health check all reduce their own type to this, so one card renders every one of them.
 */
internal data class BuddyRecommendationCardModel(
  val title: String,
  val description: String,
  val severity: Severity,
  val statusLabel: String? = null,
  val timestampLabel: String? = null,
  val unread: Boolean = false,
  val performance: BuddyRecommendationPerformanceModel? = null,
)

/**
 * The duration of the span of the recommendation, next to the durations the same span op has in
 * production. Only spans have one, and only when the bridge could query every percentile.
 */
internal data class BuddyRecommendationPerformanceModel(
  val stats: SpanDurationStats,
  val spanOp: String? = null,
  val link: String? = null,
)

/** One button of the card: an action of the recommendation, with the label the bridge gave it. */
internal data class BuddyRecommendationActionModel(
  val id: String,
  val label: String,
  val isActionableForSeer: Boolean,
  val onClick: () -> Unit,
)

internal enum class BuddyRecommendationCardStyle {
  FLOW_INSIGHT,
  ACTION_INBOX,
}

private enum class BuddyRecommendationPillEmphasis {
  PRIMARY,
  SECONDARY,
  GHOST,
}

/**
 * A recommendation card. Every button is optional: pass null, or an empty action list, and the
 * button is left out, so the caller decides what a recommendation can do rather than the card
 * guessing.
 */
@Composable
internal fun BuddyRecommendationCard(
  model: BuddyRecommendationCardModel,
  modifier: Modifier = Modifier,
  actions: List<BuddyRecommendationActionModel> = emptyList(),
  onDismiss: (() -> Unit)? = null,
  onOpenLink: (() -> Unit)? = null,
  onOpenPerformanceLink: ((String) -> Unit)? = null,
  openLinkLabel: String = "Open Link",
  detailsLabel: String = "Details",
  style: BuddyRecommendationCardStyle = BuddyRecommendationCardStyle.FLOW_INSIGHT,
) {
  val color = recommendationAccentColor(model.severity)
  val metadataLabels = model.statusLabel?.split(" • ").orEmpty().filter { it.isNotEmpty() }
  val cardColor =
    if (style == BuddyRecommendationCardStyle.ACTION_INBOX) Color.White
    else color.copy(alpha = 0.08f)
  var detailsExpanded by rememberSaveable(model.title, model.description) { mutableStateOf(false) }
  var dismissing by rememberSaveable(model.title, model.description) { mutableStateOf(false) }
  val dismissOffset = remember(model.title, model.description) { Animatable(0f) }
  val dismissScope = rememberCoroutineScope()
  val dismissArtActionKey = remember(actions) { actions.dismissArtKey() }
  val dismissArtResource =
    remember(model, dismissArtActionKey) { recommendationDismissArt(model, dismissArtActionKey) }
  val currentOnDismiss by rememberUpdatedState(onDismiss)
  val canDismiss = onDismiss != null
  LaunchedEffect(dismissing) {
    if (dismissing) {
      delay(RECOMMENDATION_DISMISS_ANIMATION_MS.toLong())
      currentOnDismiss?.invoke()
    }
  }

  AnimatedVisibility(
    visible = !dismissing,
    enter = EnterTransition.None,
    exit =
      shrinkVertically(
        animationSpec = tween(RECOMMENDATION_DISMISS_ANIMATION_MS),
        shrinkTowards = Alignment.Top,
      ) + fadeOut(animationSpec = tween(durationMillis = 120)),
  ) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().clipToBounds()) {
      val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
      if (canDismiss) {
        Image(
          painter = painterResource(id = dismissArtResource),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.matchParentSize().offset {
              IntOffset((dismissOffset.value + widthPx).roundToInt(), 0)
            },
        )
      }
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .offset { IntOffset(dismissOffset.value.roundToInt(), 0) }
            .swipeToDismissRecommendation(canDismiss, dismissOffset, dismissScope) {
              dismissing = true
            },
        color = cardColor,
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder(),
        shadowElevation = 3.dp,
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(color))
          Column(
            modifier =
              Modifier.fillMaxWidth().padding(16.dp).animateContentSize(animationSpec = tween(120)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (model.timestampLabel != null || metadataLabels.isNotEmpty()) {
              RecommendationMetadataRow(
                timestampLabel = model.timestampLabel,
                labels = metadataLabels,
                severity = model.severity,
              )
            }
            Text(
              model.title,
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
              color = BuddyInk,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            model.performance?.let { performance ->
              RecommendationPerformanceSection(
                performance = performance,
                onOpenLink = onOpenPerformanceLink,
              )
            }
            RecommendationActionRow(
              actions = actions,
              onOpenLink = onOpenLink,
              openLinkLabel = openLinkLabel,
              accentColor = color,
            )
            RecommendationDetailsDisclosure(
              label = detailsLabel,
              description = model.description,
              expanded = detailsExpanded,
              onExpandedChange = { detailsExpanded = it },
            )
          }
        }
      }
    }
  }
}

private fun recommendationDismissArt(model: BuddyRecommendationCardModel, actionKey: String): Int {
  val key =
    listOf(
        model.title,
        model.description,
        model.severity.value,
        model.statusLabel.orEmpty(),
        model.timestampLabel.orEmpty(),
        model.unread.toString(),
        model.performance?.spanOp.orEmpty(),
        model.performance?.link.orEmpty(),
        model.performance?.stats.toString(),
        actionKey,
      )
      .joinToString("|")
  val index = key.stableHashIndex()
  return when (index) {
    0 -> R.drawable.buddy_attention_android_anr
    1 -> R.drawable.buddy_attention_tombstone_support
    2 -> R.drawable.buddy_attention_ai_momentum
    3 -> R.drawable.buddy_attention_seer_helps
    4 -> R.drawable.buddy_attention_snapshot
    5 -> R.drawable.buddy_attention_nextjs_otel
    6 -> R.drawable.buddy_attention_auth_doorway
    7 -> R.drawable.buddy_attention_black_friday
    else -> R.drawable.buddy_attention_startups
  }
}

private fun List<BuddyRecommendationActionModel>.dismissArtKey(): String =
  joinToString("|") { action -> "${action.id}:${action.label}:${action.isActionableForSeer}" }

private fun String.stableHashIndex(): Int {
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  val hash =
    digest.fold(0) { acc, byte -> ((acc * 31) + (byte.toInt() and 0xff)) and Int.MAX_VALUE }
  return hash % EMPTY_ATTENTION_ART_VARIANTS
}

private fun Modifier.swipeToDismissRecommendation(
  enabled: Boolean,
  dismissOffset: Animatable<Float, *>,
  dismissScope: CoroutineScope,
  onDismiss: () -> Unit,
): Modifier {
  if (!enabled) {
    return this
  }
  return pointerInput(dismissOffset) {
    detectDragGestures(
      onDragEnd = {
        val dismissDistance = size.width.toFloat()
        val shouldDismiss = abs(dismissOffset.value) > dismissDistance * 0.35f
        dismissScope.launch {
          if (shouldDismiss) {
            dismissOffset.animateTo(
              -dismissDistance,
              animationSpec = tween(durationMillis = RECOMMENDATION_DISMISS_ANIMATION_MS),
            )
            onDismiss()
          } else {
            dismissOffset.animateTo(0f, animationSpec = tween(durationMillis = 160))
          }
        }
      },
      onDragCancel = {
        dismissScope.launch { dismissOffset.animateTo(0f, animationSpec = tween(160)) }
      },
    ) { change, dragAmount ->
      change.consume()
      val dismissDistance = size.width.toFloat()
      val nextOffset = (dismissOffset.value + dragAmount.x).coerceIn(-dismissDistance, 0f)
      dismissScope.launch { dismissOffset.snapTo(nextOffset) }
    }
  }
}

@Composable
private fun RecommendationMetadataRow(
  timestampLabel: String?,
  labels: List<String>,
  severity: Severity,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (timestampLabel != null) {
      Text(
        timestampLabel,
        modifier = Modifier.weight(1f),
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    Row(
      modifier = if (timestampLabel == null) Modifier.fillMaxWidth() else Modifier,
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      labels.forEach { label -> RecommendationMetadataPill(label, metadataColor(label, severity)) }
    }
  }
}

@Composable
private fun RecommendationMetadataPill(label: String, color: Color) {
  Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp)) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      color = color,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun metadataColor(label: String, severity: Severity): Color =
  when (label) {
    severity.value -> recommendationAccentColor(severity)
    else -> BuddyMuted
  }

private fun recommendationAccentColor(severity: Severity): Color =
  when (severity) {
    Severity.LOW -> BuddyReplayBlueHighlight
    Severity.MEDIUM -> BuddyBridgeOrange
    Severity.HIGH -> BuddyRecommendationRed
  }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationActionRow(
  actions: List<BuddyRecommendationActionModel>,
  onOpenLink: (() -> Unit)?,
  openLinkLabel: String,
  accentColor: Color,
) {
  if (actions.isEmpty() && onOpenLink == null) {
    return
  }
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    actions.forEachIndexed { index, action ->
      RecommendationActionPill(
        label = action.label,
        onClick = action.onClick,
        emphasis =
          if (index == 0) BuddyRecommendationPillEmphasis.PRIMARY
          else BuddyRecommendationPillEmphasis.SECONDARY,
        icon = if (action.isActionableForSeer) Icons.wand_shine else Icons.open_in_new,
        accentColor = accentColor,
      )
    }
    onOpenLink?.let {
      RecommendationActionPill(
        label = openLinkLabel,
        onClick = it,
        emphasis =
          if (actions.isEmpty()) BuddyRecommendationPillEmphasis.PRIMARY
          else BuddyRecommendationPillEmphasis.SECONDARY,
        icon = Icons.open_in_new,
        accentColor = accentColor,
      )
    }
  }
}

@Composable
private fun RecommendationActionPill(
  label: String,
  onClick: () -> Unit,
  emphasis: BuddyRecommendationPillEmphasis,
  icon: ImageVector?,
  accentColor: Color,
) {
  val shape = RoundedCornerShape(18.dp)
  val background =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.PRIMARY -> accentColor
      BuddyRecommendationPillEmphasis.SECONDARY -> accentColor.copy(alpha = 0.10f)
      BuddyRecommendationPillEmphasis.GHOST -> Color.Transparent
    }
  val contentColor =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.PRIMARY -> Color.White
      BuddyRecommendationPillEmphasis.SECONDARY -> accentColor
      BuddyRecommendationPillEmphasis.GHOST -> BuddyMuted
    }
  val border =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.SECONDARY ->
        BorderStroke(1.dp, accentColor.copy(alpha = 0.22f))
      BuddyRecommendationPillEmphasis.GHOST -> BorderStroke(1.dp, BuddyBorder)
      else -> null
    }
  Surface(
    modifier = Modifier.clip(shape).clickable(onClick = onClick),
    color = background,
    shape = shape,
    border = border,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        label,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(13.dp),
        )
      }
    }
  }
}

/**
 * The chart carries the numbers, so the section around it only names the span op and offers the
 * explore query that produced the production data.
 */
@Composable
private fun RecommendationPerformanceSection(
  performance: BuddyRecommendationPerformanceModel,
  onOpenLink: ((String) -> Unit)?,
) {
  Surface(color = BuddyCode, shape = RoundedCornerShape(12.dp)) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          performance.spanOp ?: "This span",
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "vs production",
          color = BuddyMuted,
          style = MaterialTheme.typography.labelMedium,
        )
      }
      SpanDurationRangeChart(stats = performance.stats)
      val link = performance.link
      if (link != null && onOpenLink != null) {
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
          RecommendationActionPill(
            label = "Open in Explore",
            onClick = { onOpenLink(link) },
            emphasis = BuddyRecommendationPillEmphasis.SECONDARY,
            icon = Icons.open_in_new,
            accentColor = BuddyPurple,
          )
        }
      }
    }
  }
}

@Composable
private fun RecommendationDetailsDisclosure(
  label: String,
  description: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
) {
  val actionLabel = if (expanded) "Hide $label" else "Show $label"
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Text(
        actionLabel,
        modifier =
          Modifier.clip(RoundedCornerShape(12.dp))
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(animationSpec = tween(60)) + expandVertically(animationSpec = tween(90)),
      exit = fadeOut(animationSpec = tween(45)) + shrinkVertically(animationSpec = tween(75)),
    ) {
      Text(
        description,
        modifier = Modifier.padding(horizontal = 2.dp),
        color = BuddyMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

/** Flow analysis and health check recommendations. [pillLabel] is null for a plain severity dot. */
internal fun Recommendation.toCardModel(
  pillLabel: String? = null,
  showStatus: Boolean = true,
): BuddyRecommendationCardModel =
  BuddyRecommendationCardModel(
    title = title,
    description = description,
    severity = severity,
    statusLabel =
      listOfNotNull(
          pillLabel?.takeIf { showStatus },
          severity.value.takeIf { it.isNotEmpty() },
          status.value.takeIf { it.isNotEmpty() },
        )
        .joinToString(" • "),
    performance = performanceCharacteristics?.toPerformanceModel(),
  )

internal fun BuddyHomeRecommendation.toCardModel(nowMs: Long): BuddyRecommendationCardModel =
  BuddyRecommendationCardModel(
    title = title,
    description = description,
    severity = severity,
    statusLabel =
      listOfNotNull(
          source.label.takeIf { it.isNotEmpty() },
          severity.value.takeIf { it.isNotEmpty() },
          status.value.takeIf { it.isNotEmpty() },
        )
        .joinToString(" • "),
    timestampLabel = relativeTime(updatedAtMs, nowMs),
    unread = unread && isOpen,
    performance = performanceCharacteristics?.toPerformanceModel(),
  )

/** Without a full set of percentiles there is nothing to draw, so the section stays away. */
private fun PerformanceCharacteristics.toPerformanceModel(): BuddyRecommendationPerformanceModel? =
  toSpanDurationStats()?.let { stats ->
    BuddyRecommendationPerformanceModel(stats = stats, spanOp = spanOp, link = link)
  }

@Composable
internal fun BuddyRecommendationErrorCard(message: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = BuddyRed.copy(alpha = 0.08f),
    shape = RoundedCornerShape(12.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Text(
      message,
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      color = BuddyRed,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** Open Seer runs get their own button label, so the reader knows where the link goes. */
internal fun openLinkLabelFor(seerRunUrl: String?): String =
  if (seerRunUrl != null) "Open Seer Run" else "Open Link"

/**
 * A link on an action points at something that already exists — a dashboard, a trace, an explore
 * query — so the button opens it rather than asking the bridge to start a Seer run.
 */
internal fun List<RecommendationAction>.toActionModels(
  onExecute: (String) -> Unit,
  onOpenLink: (String) -> Unit,
): List<BuddyRecommendationActionModel> =
  filter { it.status == ActionStatus.OPEN }
    .map { action ->
      BuddyRecommendationActionModel(action.id, action.actionLabel, action.actionableForSeer) {
        val link = action.link
        if (action.actionableForSeer) {
          onExecute(action.id)
        } else if (link != null) {
          onOpenLink(link)
        }
      }
    }

/** A dismissed recommendation keeps its card, but offers no buttons any more. */
internal fun Recommendation.isOpen(): Boolean = status == RecommendationStatus.OPEN

/** The newest Seer run of the recommendation, so the card can offer one link to it. */
internal fun Recommendation.seerRunUrl(): String? =
  actions.lastOrNull { it.seerRunUrl != null }?.seerRunUrl

@Preview(name = "Recommendation · flow analysis", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardPreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewRecommendation.toCardModel(),
      actions = previewRecommendation.actions.toActionModels(onExecute = {}, onOpenLink = {}),
      onDismiss = {},
      onOpenLink = {},
    )
  }
}

@Preview(name = "Recommendation · span performance", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardPerformancePreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewSpanRecommendation.toCardModel(),
      onOpenLink = {},
      onOpenPerformanceLink = {},
    )
  }
}

@Preview(name = "Recommendation · health check", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardHealthCheckPreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model =
        previewRecommendation.toCardModel(
          pillLabel = previewRecommendation.severity.value,
          showStatus = false,
        ),
      onOpenLink = {},
    )
  }
}

@Preview(name = "Recommendation · home list", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardHomePreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewHomeRecommendation.toCardModel(PREVIEW_NOW_MS),
      actions = previewHomeRecommendation.actions.toActionModels(onExecute = {}, onOpenLink = {}),
      onDismiss = {},
      onOpenLink = {},
    )
  }
}
