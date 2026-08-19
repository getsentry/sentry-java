package io.sentry.android.buddy.model

import java.util.Date

internal data class BuddyObservedTransaction(
  val recordingId: String?,
  val operation: String?,
  val transactionName: String?,
  val spans: List<BuddyObservedSpan>,
  val timestamp: Date,
)

internal data class BuddyObservedSpan(
  val id: String,
  val timestamp: Date,
  val operation: String,
  val description: String?,
  val data: Map<String, Any?>,
)

internal data class BuddyObservedBreadcrumb(
  val timestamp: Date,
  val type: String?,
  val category: String?,
  val data: Map<String, Any?>,
)

internal data class BuddyObservedEvent(
  val timestamp: Date,
  val title: String?,
  val data: Map<String, Any?>,
)
