package io.sentry.android.buddy.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.model.AnalysisStatus
import io.sentry.android.buddy.model.BuddyAnalysisResponse
import io.sentry.android.buddy.model.BuddyAppInfo
import io.sentry.android.buddy.model.BuddyDeviceInfo
import io.sentry.android.buddy.model.BuddyFlowImportance
import io.sentry.android.buddy.model.BuddyFlowIntent
import io.sentry.android.buddy.model.BuddyFlowRecording
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.BuddyInsight
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddyRecommendationSource
import io.sentry.android.buddy.model.BuddyRecordingMetadata
import io.sentry.android.buddy.model.BuddyRecordingResult
import io.sentry.android.buddy.model.BuddyRecordingSummary
import io.sentry.android.buddy.model.BuddySentryCorrelation
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.FlowAnalysisRequest
import io.sentry.android.buddy.model.FlowAnalysisResponse
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
import io.sentry.android.buddy.model.SentryIssue
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.timeline.BuddyTimelineRow
import io.sentry.android.buddy.ui.common.timeline.BuddyTimelineTone
import java.util.Date

/** Wraps a preview in the same theme and padding the sheets get at runtime. */
@Composable
internal fun BuddyPreviewSurface(content: @Composable () -> Unit) {
  MaterialTheme {
    Surface(color = Color.White) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        content()
      }
    }
  }
}

internal const val PREVIEW_NOW_MS = 1_700_000_000_000L

internal val previewTimelineRows: List<BuddyTimelineRow> =
  listOf(
    BuddyTimelineRow(
      id = 1,
      stamp = "0:31",
      detail = "GET /feed",
      trailing = "1.86 s",
      tone = BuddyTimelineTone.WARNING,
    ),
    BuddyTimelineRow(
      id = 2,
      stamp = "0:24",
      detail = "SELECT * FROM cache",
      trailing = "41 ms",
    ),
    BuddyTimelineRow(id = 3, stamp = "0:19", detail = "Login → Home"),
    BuddyTimelineRow(
      id = 4,
      stamp = "0:14",
      detail = "POST /auth",
      trailing = "3.42 s",
      tone = BuddyTimelineTone.WARNING,
    ),
    BuddyTimelineRow(
      id = 5,
      stamp = "0:12",
      detail = "IllegalStateException",
      tone = BuddyTimelineTone.ERROR,
      link = "https://sentry.io/issues/1",
    ),
    BuddyTimelineRow(id = 6, stamp = "0:09", detail = "#btn_sign_in"),
    BuddyTimelineRow(
      id = 7,
      stamp = "0:03",
      detail = "LoginActivity",
      trailing = "128 ms",
    ),
  )

internal val previewLiveFeed: BuddyLiveFeed =
  BuddyLiveFeed(
      items =
        listOf(
          previewLiveFeedItem(
            id = 3,
            category = BuddyLiveFeedItem.Category.ERROR,
            name = "IllegalStateException",
            elapsedMs = 12_000,
            severity = Severity.HIGH,
            adverse = true,
            data = mapOf("event_id" to "abc123"),
          ),
          previewLiveFeedItem(
            id = 2,
            category = BuddyLiveFeedItem.Category.FAILED_HTTP,
            name = "POST /auth",
            elapsedMs = 14_000,
            severity = Severity.MEDIUM,
            adverse = true,
            data = mapOf("op" to "http.client", "duration_ms" to 3_420L),
          ),
          previewLiveFeedItem(
            id = 1,
            category = BuddyLiveFeedItem.Category.SCREEN,
            name = "LoginActivity",
            elapsedMs = 3_000,
          ),
        ),
      unviewedAdverseCount = 2,
    )
    .let { feed ->
      feed.copy(
        latestAdverseItem = feed.items.first(),
        latestUnviewedAdverseItem = feed.items.first(),
      )
    }

internal val previewSentryUiLinks: BuddySentryUiLinks =
  BuddySentryUiLinks(organizationSlug = "acme", projectId = "1234")

internal val previewRecommendation: Recommendation =
  Recommendation(
    id = "tracing-disabled",
    title = "Turn on tracing for performance visibility",
    description =
      "Buddy could not find a traces sample rate or traces sampler, so transaction tracing is " +
        "likely off. Set options.tracesSampleRate or options.tracesSampler.",
    severity = Severity.MEDIUM,
    actions =
      listOf(
        RecommendationAction(
          id = "tracing-disabled:fix",
          actionLabel = "Turn on tracing",
          description =
            "Set options.tracesSampleRate, or install a tracesSampler, so transactions are " +
              "recorded.",
        )
      ),
  )

internal val previewHomeRecommendation: BuddyHomeRecommendation =
  BuddyHomeRecommendation(
    id = "health-check:tracing-disabled",
    source = BuddyRecommendationSource.HEALTH_CHECK,
    title = previewRecommendation.title,
    description = previewRecommendation.description,
    severity = previewRecommendation.severity,
    updatedAtMs = PREVIEW_NOW_MS - 60_000,
    actions = previewRecommendation.actions,
  )

private fun previewLiveFeedItem(
  id: Long,
  category: BuddyLiveFeedItem.Category,
  name: String,
  elapsedMs: Long,
  severity: Severity = Severity.LOW,
  adverse: Boolean = false,
  data: Map<String, Any?> = emptyMap(),
): BuddyLiveFeedItem =
  BuddyLiveFeedItem(
    id = id,
    timelineItem =
      BuddyTimelineItem(
        type = BuddyTimelineItem.Type.SPAN,
        timestamp = Date(PREVIEW_NOW_MS - elapsedMs),
        elapsedMs = elapsedMs,
        name = name,
        data = data,
      ),
    category = category,
    severity = severity,
    adverse = adverse,
    visibleScreens = listOf("LoginActivity"),
  )

internal val previewEmptyLiveFeed: BuddyLiveFeed = BuddyLiveFeed()

/** Three medium signals waiting, so the bubble shows a count badge in the warning palette. */
internal val previewUnreadLiveFeed: BuddyLiveFeed =
  previewLiveFeedOf(count = 3, severity = Severity.MEDIUM)

/** One high signal, which promotes the bubble to the severe "!" badge. */
internal val previewSevereLiveFeed: BuddyLiveFeed =
  previewLiveFeedOf(count = 1, severity = Severity.HIGH)

/** More signals than the badge can spell out, so it collapses to "9+". */
internal val previewBusyLiveFeed: BuddyLiveFeed =
  previewLiveFeedOf(count = 12, severity = Severity.MEDIUM)

private val previewRecording: BuddyFlowRecording =
  BuddyFlowRecording(
    flow =
      BuddyFlowIntent(
        name = "Sign in",
        developerGoal = "Find out why the first sign in feels slow",
        importance = BuddyFlowImportance.BUSINESS_CRITICAL,
      ),
    recording =
      BuddyRecordingMetadata(
        id = "3f9c1b7e",
        source = BuddyRecordingMetadata.MANUAL_DEBUG_RECORDING,
        startedAt = Date(PREVIEW_NOW_MS - 31_000),
        endedAt = Date(PREVIEW_NOW_MS),
        durationMs = 31_000,
      ),
    app =
      BuddyAppInfo(
        packageName = "io.sentry.samples.android",
        versionName = "1.4.0",
        versionCode = 140,
        release = "io.sentry.samples.android@1.4.0",
        environment = "debug",
      ),
    device =
      BuddyDeviceInfo(
        manufacturer = "Google",
        model = "Pixel 8",
        osVersion = "15",
      ),
    summary =
      BuddyRecordingSummary(
        durationMs = 31_000,
        screenCount = 2,
        spanCount = 9,
        breadcrumbCount = 14,
        timelineItemCount = 7,
      ),
    timeline = previewRecordingTimeline(),
    sentry =
      BuddySentryCorrelation(
        recordingId = "3f9c1b7e",
        dsn = "https://key@o0.ingest.sentry.io/1",
        traceId = "8f2c4d6e1a3b45c7890def1234567890",
        spanId = "a1b2c3d4e5f60718",
      ),
  )

internal val previewRecordingResult: BuddyRecordingResult =
  BuddyRecordingResult(recording = previewRecording, recordingJson = "{}")

internal val previewFlowAnalysisRequest: FlowAnalysisRequest =
  FlowAnalysisRequest(
    flowId = "sign-in",
    traceIds = listOf("8f2c4d6e1a3b45c7890def1234567890"),
    startTimeMs = PREVIEW_NOW_MS - 31_000,
    endTimeMs = PREVIEW_NOW_MS,
    dsn = "https://key@o0.ingest.sentry.io/1",
    userAnnotation = "Sign in feels slow on a cold start",
    sdk = "sentry.java.android@8.0.0",
    events = emptyList(),
  )

internal val previewFlowAnalysisSubmission: FlowAnalysisResponse =
  FlowAnalysisResponse(flowId = "sign-in", status = AnalysisStatus.PROCESSING)

internal val previewFlowAnalysis: FlowAnalysisResponse =
  FlowAnalysisResponse(
    flowId = "sign-in",
    status = AnalysisStatus.COMPLETED,
    title = "Your flow is ready for review.",
    recommendations = listOf(previewRecommendation),
    issues =
      listOf(
        SentryIssue(
          id = "5892011",
          title = "IllegalStateException: Session token missing",
          culprit = "LoginViewModel.signIn",
          count = 48,
          level = "error",
          permalink = "https://acme.sentry.io/issues/5892011/",
        )
      ),
  )

internal val previewAnalysisResponse: BuddyAnalysisResponse =
  BuddyAnalysisResponse(
    summary =
      "Sign in spends most of its time waiting on POST /auth. The request is retried once, " +
        "which pushes the flow past three seconds.",
    insights =
      listOf(
        BuddyInsight(
          title = "POST /auth dominates the flow",
          body = "3.42 s of the 31 s recording is one request, retried once after a 500.",
          severity = Severity.HIGH,
          elapsedMs = 14_000,
        ),
        BuddyInsight(
          title = "LoginActivity has no screen transaction",
          body = "Buddy could not attribute the first 3 s to a screen load.",
          severity = Severity.MEDIUM,
          elapsedMs = 3_000,
        ),
      ),
    recommendations = listOf(previewRecommendation),
  )

private fun previewRecordingTimeline(): List<BuddyTimelineItem> =
  listOf(
    previewTimelineItem(BuddyTimelineItem.Type.RECORDING_STARTED, "Sign in", 0),
    previewTimelineItem(
      BuddyTimelineItem.Type.SCREEN,
      "LoginActivity",
      3_000,
      mapOf("op" to "ui.load", "duration_ms" to 128L),
    ),
    previewTimelineItem(
      BuddyTimelineItem.Type.BREADCRUMB,
      "#btn_sign_in",
      9_000,
      mapOf("op" to "ui.click"),
    ),
    previewTimelineItem(BuddyTimelineItem.Type.EVENT, "IllegalStateException", 12_000),
    previewTimelineItem(
      BuddyTimelineItem.Type.SPAN,
      "POST /auth",
      14_000,
      mapOf("op" to "http.client", "duration_ms" to 3_420L),
    ),
    previewTimelineItem(
      BuddyTimelineItem.Type.SCREEN,
      "HomeActivity",
      19_000,
      mapOf("op" to "navigation"),
    ),
    previewTimelineItem(BuddyTimelineItem.Type.RECORDING_STOPPED, "Sign in", 31_000),
  )

private fun previewTimelineItem(
  type: BuddyTimelineItem.Type,
  name: String,
  elapsedMs: Long,
  data: Map<String, Any?> = emptyMap(),
): BuddyTimelineItem =
  BuddyTimelineItem(
    type = type,
    timestamp = Date(PREVIEW_NOW_MS - 31_000 + elapsedMs),
    elapsedMs = elapsedMs,
    name = name,
    data = data,
  )

private fun previewLiveFeedOf(count: Int, severity: Severity): BuddyLiveFeed {
  val items =
    List(count) { index ->
      previewLiveFeedItem(
        id = (count - index).toLong(),
        category = BuddyLiveFeedItem.Category.FAILED_HTTP,
        name = "POST /auth",
        elapsedMs = 14_000L + index * 1_000L,
        severity = severity,
        adverse = true,
        data = mapOf("op" to "http.client", "duration_ms" to 3_420L),
      )
    }
  return BuddyLiveFeed(
    items = items,
    unviewedAdverseCount = count,
    latestAdverseItem = items.firstOrNull(),
    latestUnviewedAdverseItem = items.firstOrNull(),
  )
}
