package io.sentry.util

class PlatformTestManipulator {
  companion object {
    fun pretendIsAndroid(isAndroid: Boolean) {
      Platform.isAndroid = isAndroid
    }
  }
}
