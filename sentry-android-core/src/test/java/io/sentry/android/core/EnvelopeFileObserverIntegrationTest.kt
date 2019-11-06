package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.core.SentryOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverIntegrationTest {
    @Test
    fun `when instance from getOutboxFileObserver, options getOutboxPath is used`() {
        var options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getOutboxFileObserver()
        assertEquals(options.outboxPath, sut.getPath(options))
    }

    @Test
    fun `when instance from getCachedEnvelopeFileObserver, options getCacheDirPath + cache dir is used`() {
        var options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getCachedEnvelopeFileObserver()
        assertEquals(options.cacheDirPath + File.separator + "cached", sut.getPath(options))
    }
}
