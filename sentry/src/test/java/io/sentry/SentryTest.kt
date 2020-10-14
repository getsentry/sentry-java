package io.sentry

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.rules.TemporaryFolder

class SentryTest {

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
    }

    // todo: remove this test
    @Test
    fun `foo`() {
        Sentry.init {
            it.dsn = "https://9eda1857f9344d51821b656ba3557780@o420886.ingest.sentry.io/5339853"
            it.isDebug = true
        }
        val contexts = TransactionContexts()
        contexts.trace = Trace()
        contexts.trace.op = "http"
        contexts.trace.description = "some request"
        contexts.trace.status = SpanStatus.OK
        contexts.trace.setTag("myTag", "myValue")
        val transaction = Transaction("newtranxs4", contexts)
        transaction.sdk = SentryOptions().sdkVersion
        Thread.sleep(100)
        transaction.finish()
        Sentry.captureTransaction(transaction, null)
        Sentry.captureException(RuntimeException("oops"))
    }

    @Test
    fun `outboxDir should be created at initialization`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.cacheDirPath = getTempPath()
            sentryOptions = it
        }

        val file = File(sentryOptions!!.outboxPath!!)
        assertTrue(file.exists())
        file.deleteOnExit()
    }

    @Test
    fun `envelopesDir should be created at initialization`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.cacheDirPath = getTempPath()
            sentryOptions = it
        }

        val file = File(sentryOptions!!.cacheDirPath!!)
        assertTrue(file.exists())
        file.deleteOnExit()
    }

    @Test
    fun `Init sets SystemOutLogger if logger is NoOp and debug is enabled`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.cacheDirPath = getTempPath()
            sentryOptions = it
            it.isDebug = true
        }

        assertTrue((sentryOptions!!.logger as DiagnosticLogger).logger is SystemOutLogger)
    }

    @Test
    fun `Init sets GsonSerializer if serializer is NoOp`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.cacheDirPath = getTempPath()
            sentryOptions = it
        }

        assertTrue(sentryOptions!!.serializer is GsonSerializer)
    }

    @Test
    fun `scope changes are isolated to a thread`() {
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
        }
        Sentry.configureScope {
            it.setTag("a", "a")
        }

        CompletableFuture.runAsync {
            Sentry.configureScope {
                it.setTag("b", "b")
            }

            Sentry.configureScope {
                assertEquals(setOf("a", "b"), it.tags.keys)
            }
        }.get()

        Sentry.configureScope {
            assertEquals(setOf("a"), it.tags.keys)
        }
    }

    @Test
    fun `warns about multiple Sentry initializations`() {
        val logger = mock<ILogger>()
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
        }
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.isDebug = true
            it.setLogger(logger)
        }
        verify(logger).log(eq(SentryLevel.WARNING), eq("Sentry has been already initialized. Previous configuration will be overwritten."))
    }

    @Test
    fun `initializes Sentry using external properties`() {
        // create a sentry.properties file in temporary folder
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val file = temporaryFolder.newFile("sentry.properties")
        file.writeText("dsn=http://key@localhost/proj")
        // set location of the sentry.properties file
        System.setProperty("sentry.properties.file", file.absolutePath)

        try {
            // initialize Sentry with empty DSN and enable loading properties from external sources
            Sentry.init {
                it.isEnableExternalConfiguration = true
            }
            assertTrue(HubAdapter.getInstance().isEnabled)
        } finally {
            temporaryFolder.delete()
        }
    }

    private fun getTempPath(): String {
        val tempFile = Files.createTempDirectory("cache").toFile()
        tempFile.delete()

        // sanity check
        assertFalse(tempFile.exists())
        return tempFile.absolutePath
    }
}
