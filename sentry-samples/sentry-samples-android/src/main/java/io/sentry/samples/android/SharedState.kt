package io.sentry.samples.android

object SharedState {
  @Volatile
  var isOrientationChange: Boolean = false
}