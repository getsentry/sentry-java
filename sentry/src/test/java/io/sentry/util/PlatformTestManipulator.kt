package io.sentry.util

class PlatformTestManipulator {
    companion object {
        fun pretendJavaNinePlus(isJavaNinePlus: Boolean) {
            Platform.isJavaNinePlus = isJavaNinePlus
        }

        fun pretendIsAndroid(isAndroid: Boolean) {
            Platform.isAndroid = isAndroid
        }
    }
}
