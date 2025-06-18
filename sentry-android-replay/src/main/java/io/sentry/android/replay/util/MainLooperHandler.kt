package io.sentry.android.replay.util

import android.os.Handler
import android.os.Looper

internal class MainLooperHandler(
    looper: Looper = Looper.getMainLooper(),
) {
    val handler = Handler(looper)

    fun post(runnable: Runnable) {
        handler.post(runnable)
    }
}
