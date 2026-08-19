package io.sentry.android.buddy.ui.common

import java.util.Locale

internal fun relativeTime(timestampMs: Long, nowMs: Long): String {
  val ageMs = (nowMs - timestampMs).coerceAtLeast(0)
  if (ageMs < 1000) {
    return "just now"
  }
  val ageSeconds = ageMs / 1000
  if (ageSeconds < 60) {
    return "${ageSeconds}s ago"
  }
  return "${ageSeconds / 60}m ago"
}

internal fun formatDurationValue(durationMs: Long): String {
  if (durationMs < 1000) {
    return "${durationMs}ms"
  }
  val seconds = durationMs / 1000f
  return String.format(Locale.ROOT, "%.2fs", seconds)
}

internal fun String.humanizeDotKey(): String =
  split('.', '_', '-', ' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }

internal fun formatElapsed(durationMs: Long): String {
  val boundedMs = durationMs.coerceAtLeast(0)
  val totalSeconds = boundedMs / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

internal fun Float.constrain(min: Float, max: Float): Float {
  if (max < min) {
    return 0f
  }
  return coerceIn(min, max)
}

internal fun Map<String, Any?>.mapValue(key: String): Map<*, *> =
  this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

internal fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

internal fun Map<*, *>.longValue(key: String): Long? =
  when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
  }
