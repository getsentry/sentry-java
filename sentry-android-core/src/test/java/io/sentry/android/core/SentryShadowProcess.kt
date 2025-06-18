package io.sentry.android.core

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(android.os.Process::class)
class SentryShadowProcess {
    companion object {
        private var startupTimeMillis: Long = 0

        fun setStartUptimeMillis(value: Long) {
            startupTimeMillis = value
        }

        @Suppress("unused")
        @Implementation
        @JvmStatic
        fun getStartUptimeMillis(): Long = startupTimeMillis
    }
}
