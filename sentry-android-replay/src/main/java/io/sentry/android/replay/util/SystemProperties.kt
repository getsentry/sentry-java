package io.sentry.android.replay.util

import android.annotation.SuppressLint
import android.os.Build
import java.lang.reflect.Method

internal object SystemProperties {
    // from https://cs.android.com/android/platform/superproject/main/+/main:out/soong/.intermediates/system/libsysprop/srcs/PlatformProperties/android_common/xref/srcjars.xref/android/sysprop/SocProperties.java;l=163-171
    // these props are not available on API < 31 via Build, so we use reflection to access them
    const val SOC_MODEL = "ro.soc.model"
    const val SOC_MANUFACTURER = "ro.soc.manufacturer"

    @delegate:SuppressLint("PrivateApi")
    private val getProperty: Method by lazy {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java)
    }

    fun get(key: String, defaultValue: String = ""): String {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                getProperty.invoke(null, key) as? String ?: defaultValue
            } catch (e: Throwable) {
                defaultValue
            }
        } else {
            when (key) {
                SOC_MODEL -> Build.SOC_MODEL
                SOC_MANUFACTURER -> Build.SOC_MANUFACTURER
                else -> throw IllegalArgumentException("Unknown system property: $key")
            }
        }
    }
}
