package io.sentry.util

object Apollo4PlatformTestManipulator {

    fun pretendIsAndroid(isAndroid: Boolean) {
        Platform.isAndroid = isAndroid
    }
}
