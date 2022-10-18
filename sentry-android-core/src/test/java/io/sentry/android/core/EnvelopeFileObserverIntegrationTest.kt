package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hub
import io.sentry.SentryOptions
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverIntegrationTest {

    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    }

    @AfterTest
    fun shutdown() {
        Files.delete(file.toPath())
    }

    @Test
    fun `when instance from getOutboxFileObserver, options getOutboxPath is used`() {
        val options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getOutboxFileObserver()
        assertEquals(options.outboxPath, sut.getPath(options))
    }

    @Test
    fun `when hub is closed, integrations should be closed`() {
        val integrationMock = mock<EnvelopeFileObserverIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.cacheDirPath = file.absolutePath
        options.addIntegration(integrationMock)
        options.setSerializer(mock())
//        val expected = HubAdapter.getInstance()
        val hub = Hub(options)
//        verify(integrationMock).register(expected, options)
        hub.close()
        verify(integrationMock).close()
    }
}
