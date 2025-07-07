package io.sentry.android.replay

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal class CaptureExecutor(
    private val captureOutputStream: ByteArrayOutputStream,
    private val root: View,
) : Executor {

    private val executorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "CaptureExecutorThread").apply {
            isDaemon = true
        }
    }
    val debounceTimeMillis = AtomicLong(0L)

    @SuppressLint("PrivateApi")
    override fun execute(command: Runnable) {
        val now = System.currentTimeMillis()
        if (debounceTimeMillis.get() == 0L || (debounceTimeMillis.get() + 1000L <= now)) {
            debounceTimeMillis.set(now)
            executorService.execute {
                command.run()
                val method = Picture::class.java.getDeclaredMethod("createFromStream", InputStream::class.java)
                val picture = method.invoke(null, captureOutputStream.toByteArray().inputStream()) as Picture
                val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                picture.draw(canvas)
            }
        }
    }
}
