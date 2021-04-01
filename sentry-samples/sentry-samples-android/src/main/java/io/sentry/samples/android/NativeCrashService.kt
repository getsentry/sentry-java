package io.sentry.samples.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions

class NativeCrashService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStart(intent: Intent?, startId: Int) {
        handleStart(intent, startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SentryAndroid.init(this) { println("SentryAndroid.init in Service")  }
        Thread.sleep(3000)
        handleStart(intent, startId)
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent?, startId: Int) {

        NativeSample.crash()
    }
}
