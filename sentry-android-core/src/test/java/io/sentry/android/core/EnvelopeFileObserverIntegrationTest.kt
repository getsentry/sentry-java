package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.createTestScopes
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverIntegrationTest {
    inner class Fixture {
        val scopes: IScopes = mock()
        private lateinit var options: SentryAndroidOptions
        val logger = mock<ILogger>()

        fun getSut(optionConfiguration: (SentryAndroidOptions) -> Unit = {}): EnvelopeFileObserverIntegration {
            options = SentryAndroidOptions()
            options.setLogger(logger)
            options.isDebug = true
            optionConfiguration(options)
            whenever(scopes.options).thenReturn(options)

            return object : EnvelopeFileObserverIntegration() {
                override fun getPath(options: SentryOptions): String? = file.absolutePath
            }
        }
    }

    private val fixture = Fixture()

    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    }

    @AfterTest
    fun shutdown() {
        delete(file)
    }

    private fun delete(f: File) {
        f.listFiles()?.forEach { delete(it) }
        Files.delete(f.toPath())
    }

    @Test
    fun `when instance from getOutboxFileObserver, options getOutboxPath is used`() {
        val options = SentryOptions()
        options.cacheDirPath = "some_dir"

        val sut = EnvelopeFileObserverIntegration.getOutboxFileObserver()
        assertEquals(options.outboxPath, sut.getPath(options))
    }

    @Test
    fun `when scopes is closed, integrations should be closed`() {
        val integrationMock = mock<EnvelopeFileObserverIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.cacheDirPath = file.absolutePath
        options.addIntegration(integrationMock)
        options.setSerializer(mock())
        val scopes = createTestScopes(options)
        scopes.close()
        verify(integrationMock).close()
    }

    @Test
    fun `when scopes is closed right after start, integration is not registered`() {
        val deferredExecutorService = DeferredExecutorService()
        val integration =
            fixture.getSut {
                it.executorService = deferredExecutorService
            }
        integration.register(fixture.scopes, fixture.scopes.options)
        integration.close()
        deferredExecutorService.runAll()
        verify(fixture.logger, never()).log(eq(SentryLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
    }

    @Test
    fun `register with fake executor service does not install integration`() {
        val integration =
            fixture.getSut {
                it.executorService = mock()
            }
        integration.register(fixture.scopes, fixture.scopes.options)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("Registering EnvelopeFileObserverIntegration for path: %s"),
            eq(file.absolutePath),
        )
        verify(fixture.logger, never()).log(eq(SentryLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
    }

    @Test
    fun `register integration on the background via executor service`() {
        val integration =
            fixture.getSut {
                it.executorService = ImmediateExecutorService()
            }
        integration.register(fixture.scopes, fixture.scopes.options)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("Registering EnvelopeFileObserverIntegration for path: %s"),
            eq(file.absolutePath),
        )
        verify(fixture.logger).log(eq(SentryLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
    }
}
