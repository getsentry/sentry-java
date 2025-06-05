package io.sentry.android.replay

import java.io.Closeable

interface Recorder : Closeable {
    /**
     * @param recorderConfig a [ScreenshotRecorderConfig] that can be used to determine frame rate
     * at which the screenshots should be taken, and the screenshots size/resolution, which can
     * change e.g. in the case of orientation change or window size change
     */
    fun start()

    fun onConfigurationChanged(config: ScreenshotRecorderConfig)

    fun resume()

    fun pause()

    fun reset()

    fun stop()
}
