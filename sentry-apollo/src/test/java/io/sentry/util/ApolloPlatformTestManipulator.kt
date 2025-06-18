package io.sentry.util

object ApolloPlatformTestManipulator {
    fun pretendIsAndroid(isAndroid: Boolean) {
        Platform.isAndroid = isAndroid
    }
}
