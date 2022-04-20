package io.sentry.android.uitests.end2end

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry

class EmptyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread {
            val t = Sentry.startTransaction("e2e tests", "empty onCreate")
            try {
                throw Exception("This is a test.")
            } catch (e: Exception) {
                Sentry.captureException(e)
            }

            Thread.sleep(1000)
            t.finish()
        }.start()
        Thread.sleep(5000)

    }
}
