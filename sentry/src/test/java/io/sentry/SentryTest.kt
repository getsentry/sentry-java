package io.sentry

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.SentryId
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

    private val dsn = "http://key@localhost/proj"

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `outboxDir should be created at initialization`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
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
            it.dsn = dsn
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
            it.dsn = dsn
            it.cacheDirPath = getTempPath()
            sentryOptions = it
            it.setDebug(true)
        }

        assertTrue((sentryOptions!!.logger as DiagnosticLogger).logger is SystemOutLogger)
    }

    @Test
    fun `scope changes are isolated to a thread`() {
        Sentry.init {
            it.dsn = dsn
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
            it.dsn = dsn
        }
        Sentry.init {
            it.dsn = dsn
            it.setDebug(true)
            it.setLogger(logger)
        }
        verify(logger).log(eq(SentryLevel.WARNING), eq("Sentry has been already initialized. Previous configuration will be overwritten."))
    }

    @Test
    fun `warns about multiple Sentry initializations with string overload`() {
        val logger = mock<ILogger>()
        Sentry.init(dsn)
        Sentry.init {
            it.dsn = dsn
            it.setDebug(true)
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

    @Test
    fun `captureUserFeedback gets forwarded to client`() {
        Sentry.init { it.dsn = dsn }

        val client = mock<ISentryClient>()
        Sentry.getCurrentHub().bindClient(client)

        val userFeedback = UserFeedback(SentryId.EMPTY_ID)
        Sentry.captureUserFeedback(userFeedback)

        verify(client).captureUserFeedback(argThat {
            eventId == userFeedback.eventId
        })
    }

    @Test
    fun `startTransaction sets operation and description`() {
        Sentry.init {
            it.dsn = dsn
            it.tracesSampleRate = 1.0
        }

        val transaction = Sentry.startTransaction("name", "op", "desc")
        assertEquals("name", transaction.name)
        assertEquals("op", transaction.operation)
        assertEquals("desc", transaction.description)
    }

    @Test
    fun `isCrashedLastRun returns true if crashedLastRun is set`() {
        Sentry.init {
            it.dsn = dsn
        }

        SentryCrashLastRunState.getInstance().setCrashedLastRun(true)

        assertTrue(Sentry.isCrashedLastRun()!!)
    }

    private fun getTempPath(): String {
        val tempFile = Files.createTempDirectory("cache").toFile()
        tempFile.delete()

        // sanity check
        assertFalse(tempFile.exists())
        return tempFile.absolutePath
    }
}
