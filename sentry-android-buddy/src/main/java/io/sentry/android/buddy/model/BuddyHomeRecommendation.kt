package io.sentry.android.buddy.model

internal enum class BuddyHomeTab {
  LIVE_FEED,
  RECORD_FLOW,
  ACTIONS,
}

internal enum class BuddyRecommendationSource(val label: String) {
  FLOW_ANALYSIS("flow analysis"),
  HEALTH_CHECK("health check"),
  LIVE_FEED("live feed"),
}

internal data class BuddyHomeRecommendation(
  val id: String,
  val source: BuddyRecommendationSource,
  val title: String,
  val description: String,
  val severity: Severity,
  val status: RecommendationStatus = RecommendationStatus.OPEN,
  val unread: Boolean = true,
  val updatedAtMs: Long,
  val primaryLink: String? = null,
  val actions: List<RecommendationAction> = emptyList(),
  val flowId: String? = null,
  val sourceRecommendationId: String? = null,
  val performanceCharacteristics: PerformanceCharacteristics? = null,
) {
  val isOpen: Boolean
    get() = status == RecommendationStatus.OPEN

  val isAttentionDriving: Boolean
    get() = isOpen && (actions.isEmpty() || actions.any { it.status == ActionStatus.OPEN })

  /** The bridge only knows recommendations that belong to a flow analysis. */
  val supportsRemoteActions: Boolean
    get() = flowId != null && sourceRecommendationId != null

  /** The newest Seer run of the recommendation, so the card can offer one link to it. */
  val seerRunUrl: String?
    get() = actions.lastOrNull { it.seerRunUrl != null }?.seerRunUrl
}

internal fun FlowAnalysisResponse.toHomeRecommendations(
  nowMs: Long
): List<BuddyHomeRecommendation> {
  return recommendations.map { recommendation ->
    BuddyHomeRecommendation(
      id = "flow-analysis:${recommendation.id}",
      source = BuddyRecommendationSource.FLOW_ANALYSIS,
      title = recommendation.title,
      description = recommendation.description,
      severity = recommendation.severity,
      status = recommendation.status,
      unread = recommendation.status == RecommendationStatus.OPEN,
      updatedAtMs = nowMs,
      primaryLink = recommendation.link,
      actions = recommendation.actions,
      performanceCharacteristics = recommendation.performanceCharacteristics,
      flowId = flowId,
      sourceRecommendationId = recommendation.id,
    )
  }
}

internal fun BuddyHealthCheckResponse.toHomeRecommendations(
  nowMs: Long
): List<BuddyHomeRecommendation> {
  return recommendations.map { recommendation ->
    BuddyHomeRecommendation(
      id = "health-check:${recommendation.id}",
      source = BuddyRecommendationSource.HEALTH_CHECK,
      title = recommendation.title,
      description = recommendation.description,
      severity = recommendation.severity,
      status = recommendation.status,
      unread = recommendation.status == RecommendationStatus.OPEN,
      updatedAtMs = nowMs,
      primaryLink = recommendation.link,
      actions = recommendation.actions,
      performanceCharacteristics = recommendation.performanceCharacteristics,
    )
  }
}

internal fun BuddyLiveFeed.toHomeRecommendations(
  sentryUiLinks: BuddySentryUiLinks
): List<BuddyHomeRecommendation> {
  val recommendations = linkedMapOf<String, BuddyHomeRecommendation>()
  items
    .filter { it.category == BuddyLiveFeedItem.Category.ERROR }
    .forEach { item ->
      val screenName = item.visibleScreens.lastOrNull()
      val titleSuffix = screenName?.let { " on $it" }.orEmpty()
      val key = "live-feed:error:${screenName.orEmpty()}:${item.recommendationTitle()}"
      val link = sentryUiLinks.linkFor(item)
      val action: RecommendationAction? = link?.let {
        RecommendationAction(
          "",
          "Explore",
          actionableForSeer = false,
          "",
          link = link,
          status = ActionStatus.OPEN,
          null,
        )
      }

      recommendations[key] =
        BuddyHomeRecommendation(
          id = key,
          source = BuddyRecommendationSource.LIVE_FEED,
          title = "Unhandled error$titleSuffix",
          description = item.liveFeedRecommendationDescription(),
          severity = item.severity,
          updatedAtMs = item.timestamp.time,
          actions = listOfNotNull(action),
        )
    }

  repeatedScreenRecommendations().forEach { recommendation ->
    recommendations[recommendation.id] = recommendation
  }
  return recommendations.values.sortedByDescending { it.updatedAtMs }
}

private fun BuddyLiveFeed.repeatedScreenRecommendations(): List<BuddyHomeRecommendation> {
  return items
    .filter { it.category == BuddyLiveFeedItem.Category.SCREEN }
    .groupBy { it.recommendationTitle() }
    .mapNotNull { (screenName, screenItems) ->
      if (screenItems.size < SCREEN_RECOMMENDATION_THRESHOLD) {
        return@mapNotNull null
      }
      val latestVisit = screenItems.maxByOrNull { it.timestamp.time } ?: return@mapNotNull null
      BuddyHomeRecommendation(
        id = "live-feed:screen:$screenName",
        source = BuddyRecommendationSource.LIVE_FEED,
        title = "$screenName may be missing a screen transaction",
        description =
          "Buddy has seen this screen ${screenItems.size} times recently without enough trace context to explain what happened there.",
        severity = Severity.LOW,
        updatedAtMs = latestVisit.timestamp.time,
      )
    }
}

private fun BuddyLiveFeedItem.liveFeedRecommendationDescription(): String {
  val screenName = visibleScreens.lastOrNull()
  return if (screenName == null) {
    "${recommendationTitle()} was captured in the live feed and is worth another look."
  } else {
    "${recommendationTitle()} was captured while $screenName was visible."
  }
}

private fun BuddyLiveFeedItem.recommendationTitle(): String =
  when (category) {
    BuddyLiveFeedItem.Category.SCREEN -> timelineItem.name ?: "Unknown screen"
    BuddyLiveFeedItem.Category.STEP -> timelineItem.name ?: "Unnamed step"
    BuddyLiveFeedItem.Category.ERROR -> timelineItem.name ?: "Error captured"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> recommendationHttpTitle()
    BuddyLiveFeedItem.Category.SLOW_SPAN -> timelineItem.name ?: "Slow span"
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.name ?: "Failed span"
  }

private fun BuddyLiveFeedItem.recommendationHttpTitle(): String {
  val data = timelineItem.data.mapValue("data")
  val method = data.stringValue("method") ?: data.stringValue("http.method")
  val url = data.stringValue("url") ?: data.stringValue("http.url")
  return listOfNotNull(method, url).joinToString(" ").ifBlank { timelineItem.name ?: "Failed HTTP" }
}

private fun Map<String, Any?>.mapValue(key: String): Map<*, *> =
  this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

private const val SCREEN_RECOMMENDATION_THRESHOLD = 4
