package io.sentry.util

object SpotlightPlatformTestManipulator {
  fun pretendIsAndroid(isAndroid: Boolean) {
    Platform.isAndroid = isAndroid
  }
}
