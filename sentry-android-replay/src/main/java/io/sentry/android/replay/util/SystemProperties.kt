package io.sentry.android.replay.util

import android.os.Build

internal object SystemProperties {
    enum class Property(val key: String) {
        SOC_MODEL("ro.soc.model"),
        SOC_MANUFACTURER("ro.soc.manufacturer")
    }

    fun get(key: Property, defaultValue: String = ""): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (key) {
                Property.SOC_MODEL -> Build.SOC_MODEL
                Property.SOC_MANUFACTURER -> Build.SOC_MANUFACTURER
            }
        } else {
            defaultValue
        }
    }
}
