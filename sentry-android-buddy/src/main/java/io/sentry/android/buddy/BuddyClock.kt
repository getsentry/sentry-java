package io.sentry.android.buddy

import java.util.Date
import java.util.UUID

internal interface BuddyClock {
  fun now(): Date

  fun elapsedRealtimeMillis(): Long
}

internal object SystemBuddyClock : BuddyClock {
  override fun now(): Date = Date()

  override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

internal interface BuddyIdGenerator {
  fun generate(): String
}

internal object UuidBuddyIdGenerator : BuddyIdGenerator {
  override fun generate(): String = UUID.randomUUID().toString()
}
