package io.sentry.util

object Apollo3PlatformTestManipulator {
    fun pretendIsAndroid(isAndroid: Boolean) {
        Platform.isAndroid = isAndroid
    }
}
