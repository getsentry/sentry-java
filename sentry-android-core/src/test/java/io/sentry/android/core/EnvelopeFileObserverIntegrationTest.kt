package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.Hub
import io.sentry.core.SentryOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverIntegrationTest {
    @Test
    fun `when instance from getOutboxFileObserver, options getOutboxPath is used`() {
        val options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getOutboxFileObserver()
        assertEquals(options.outboxPath, sut.getPath(options))
    }

    @Test
    fun `when instance from getCachedEnvelopeFileObserver, options getCacheDirPath + cache dir is used`() {
        val options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getCachedEnvelopeFileObserver()
        assertEquals(options.cacheDirPath + File.separator + "cached", sut.getPath(options))
    }

    @Test
    fun `when hub is closed, integrations should be closed`() {
        val integrationMock = mock<EnvelopeFileObserverIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        val hub = Hub(options)
        verify(integrationMock).register(hub, options)
        hub.close()
        verify(integrationMock, times(1)).close()
    }
}
