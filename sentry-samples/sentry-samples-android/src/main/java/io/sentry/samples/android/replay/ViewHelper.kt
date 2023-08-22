package io.sentry.samples.android.replay

import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method

object ViewHelper {

    // Based on
    // https://www.xda-developers.com/android-development-bypass-hidden-api-restrictions/
    // PorterDuffColorFilter getter methods are API private and black-listed
    // Loading them via reflection will fail, but performing reflection via reflection seems to work

    private const val TAG = "ViewHelper"

    private val forName = Class::class.java.getMethod("forName", String::class.java)
    private val getDeclaredMethod = Class::class.java.getMethod(
        "getMethod",
        String::class.java, arrayOf<Class<*>>()::class.java
    )

    private val getDeclaredField = Class::class.java.getMethod(
        "getDeclaredField",
        String::class.java
    )

    private val porterDuffColorFilterClass =
        forName.invoke(null, "android.graphics.PorterDuffColorFilter") as Class<*>

    val onDrawMethod = View::class.java.getDeclaredMethod("onDraw", Canvas::class.java).also {
        it.isAccessible = true
    }

    fun executeOnDraw(view: View, canvas: Canvas) {
        // performs drawing operations for a view, without their childs
        view.background?.draw(canvas)
        onDrawMethod.invoke(view, canvas)
    }

    fun decodePorterDuffcolorFilter(colorFilter: PorterDuffColorFilter): Pair<Int, PorterDuff.Mode>? {
        try {
            val modeField =
                getDeclaredField.invoke(porterDuffColorFilterClass, "mMode") as Field
            modeField.isAccessible = true
            val colorMethod =
                getDeclaredMethod.invoke(porterDuffColorFilterClass, "getColor", null) as Method

            val mode = modeField.get(colorFilter) as PorterDuff.Mode
            val color = colorMethod.invoke(colorFilter) as Int
            return Pair(color, mode)
        } catch (e: Exception) {
            Log.e(TAG, "failed to decode porter duff color filter, returning null")
        }
        return null;
    }
}
