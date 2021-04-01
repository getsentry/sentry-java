package io.sentry.samples.android

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import io.sentry.android.core.SentryAndroid
import java.io.File

class NativeCrashService : Service() {

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStart(intent: Intent?, startId: Int) {
        handleStart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleStart()
        return START_NOT_STICKY
    }

    private fun handleStart() {
        SentryAndroid.init(this) { options ->
            options.dsn = "https://1053864c67cc410aa1ffc9701bd6f93d@o447951.ingest.sentry.io/5428559"
        }

        // Dispatch async so method, and therefore START_NOT_STICKY, can return.
        val handler = Handler()
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            handler.post {
                NativeSample.crash()
            }
        }
    }
}
