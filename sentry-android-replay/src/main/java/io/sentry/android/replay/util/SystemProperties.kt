package io.sentry.android.replay.util

import android.os.Build

internal object SystemProperties {
  enum class Property {
    SOC_MODEL,
    SOC_MANUFACTURER,
  }

  fun get(key: Property, defaultValue: String = ""): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      when (key) {
        Property.SOC_MODEL -> Build.SOC_MODEL
        Property.SOC_MANUFACTURER -> Build.SOC_MANUFACTURER
      }
    } else {
      defaultValue
    }
}
