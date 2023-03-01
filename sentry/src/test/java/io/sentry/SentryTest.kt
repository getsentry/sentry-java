package io.sentry

import io.sentry.cache.EnvelopeCache
import io.sentry.cache.IEnvelopeCache
import io.sentry.internal.modules.CompositeModulesLoader
import io.sentry.internal.modules.IModulesLoader
import io.sentry.protocol.SentryId
import io.sentry.util.thread.IMainThreadChecker
import io.sentry.util.thread.MainThreadChecker
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryTest {

    private val dsn = "http://key@localhost/proj"

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `outboxPath should be created at initialization`() {
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
    fun `cacheDirPath should be created at initialization`() {
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

        verify(client).captureUserFeedback(
            argThat {
                eventId == userFeedback.eventId
            }
        )
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

    @Test
    fun `profilingTracesDirPath should be created and cleared at initialization when profiling is enabled`() {
        val tempPath = getTempPath()
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
            it.profilesSampleRate = 1.0
            it.cacheDirPath = tempPath
            sentryOptions = it
        }

        assertTrue(File(sentryOptions?.profilingTracesDirPath!!).exists())
        assertTrue(File(sentryOptions?.profilingTracesDirPath!!).list()!!.isEmpty())
    }

    @Test
    fun `profilingTracesDirPath should not be created and cleared when profiling is disabled`() {
        val tempPath = getTempPath()
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
            it.profilesSampleRate = 0.0
            it.cacheDirPath = tempPath
            sentryOptions = it
        }

        assertFalse(File(sentryOptions?.profilingTracesDirPath!!).exists())
    }

    @Test
    fun `using sentry before calling init creates NoOpHub but after init Sentry uses a new clone`() {
        // noop as not yet initialized, caches NoOpHub in ThreadLocal
        Sentry.captureMessage("noop caused")

        assertTrue(Sentry.getCurrentHub() is NoOpHub)

        // init Sentry in another thread
        val thread = Thread() {
            Sentry.init {
                it.dsn = dsn
                it.isDebug = true
            }
        }
        thread.start()
        thread.join()

        Sentry.captureMessage("should work now")

        val hub = Sentry.getCurrentHub()
        assertNotNull(hub)
        assertFalse(hub is NoOpHub)
    }

    @Test
    fun `main hub can be cloned and does not share scope with current hub`() {
        // noop as not yet initialized, caches NoOpHub in ThreadLocal
        Sentry.addBreadcrumb("breadcrumbNoOp")
        Sentry.captureMessage("messageNoOp")

        assertTrue(Sentry.getCurrentHub() is NoOpHub)

        val capturedEvents = mutableListOf<SentryEvent>()

        // init Sentry in another thread
        val thread = Thread() {
            Sentry.init {
                it.dsn = dsn
                it.isDebug = true
                it.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                    capturedEvents.add(event)
                    event
                }
            }
        }
        thread.start()
        thread.join()

        Sentry.addBreadcrumb("breadcrumbCurrent")

        val hub = Sentry.getCurrentHub()
        assertNotNull(hub)
        assertFalse(hub is NoOpHub)

        val newMainHubClone = Sentry.cloneMainHub()
        newMainHubClone.addBreadcrumb("breadcrumbMainClone")

        hub.captureMessage("messageCurrent")
        newMainHubClone.captureMessage("messageMainClone")

        assertEquals(2, capturedEvents.size)
        val mainCloneEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageMainClone" }
        val currentHubEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageCurrent" }

        assertNotNull(mainCloneEvent)
        assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
        assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
        assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })

        assertNotNull(currentHubEvent)
        assertNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
        assertNotNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
        assertNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })
    }

    @Test
    fun `main hub is not cloned in global hub mode and shares scope with current hub`() {
        // noop as not yet initialized, caches NoOpHub in ThreadLocal
        Sentry.addBreadcrumb("breadcrumbNoOp")
        Sentry.captureMessage("messageNoOp")

        assertTrue(Sentry.getCurrentHub() is NoOpHub)

        val capturedEvents = mutableListOf<SentryEvent>()

        // init Sentry in another thread
        val thread = Thread() {
            Sentry.init({
                it.dsn = dsn
                it.isDebug = true
                it.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                    capturedEvents.add(event)
                    event
                }
            }, true)
        }
        thread.start()
        thread.join()

        Sentry.addBreadcrumb("breadcrumbCurrent")

        val hub = Sentry.getCurrentHub()
        assertNotNull(hub)
        assertFalse(hub is NoOpHub)

        val newMainHubClone = Sentry.cloneMainHub()
        newMainHubClone.addBreadcrumb("breadcrumbMainClone")

        hub.captureMessage("messageCurrent")
        newMainHubClone.captureMessage("messageMainClone")

        assertEquals(2, capturedEvents.size)
        val mainCloneEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageMainClone" }
        val currentHubEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageCurrent" }

        assertNotNull(mainCloneEvent)
        assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
        assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
        assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })

        assertNotNull(currentHubEvent)
        assertNotNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
        assertNotNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
        assertNull(currentHubEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })
    }

    @Test
    fun `when init is called and configure throws an exception then an error is logged`() {
        val logger = mock<ILogger>()
        val initException = Exception("init")

        Sentry.init({
            it.dsn = dsn
            it.isDebug = true
            it.setLogger(logger)
            throw initException
        }, true)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(initException))
    }

    @Test
    fun `when init with a SentryOptions Subclass is called and configure throws an exception then an error is logged`() {
        class ExtendedSentryOptions : SentryOptions()

        val logger = mock<ILogger>()
        val initException = Exception("init")

        Sentry.init(OptionsContainer.create(ExtendedSentryOptions::class.java)) { options: ExtendedSentryOptions ->
            options.dsn = dsn
            options.isDebug = true
            options.setLogger(logger)
            throw initException
        }

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(initException))
    }

    @Test
    fun `overrides envelope cache if it's not set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.cacheDirPath = getTempPath()
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.envelopeDiskCache is EnvelopeCache }
    }

    @Test
    fun `does not override envelope cache if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.cacheDirPath = getTempPath()
            it.setEnvelopeDiskCache(CustomEnvelopCache())
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.envelopeDiskCache is CustomEnvelopCache }
    }

    @Test
    fun `overrides modules loader if it's not set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.modulesLoader is CompositeModulesLoader }
    }

    @Test
    fun `does not override modules loader if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.setModulesLoader(CustomModulesLoader())
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.modulesLoader is CustomModulesLoader }
    }

    @Test
    fun `overrides main thread checker if it's not set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.mainThreadChecker is MainThreadChecker }
    }

    @Test
    fun `does not override main thread checker if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.mainThreadChecker = CustomMainThreadChecker()
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.mainThreadChecker is CustomMainThreadChecker }
    }

    @Test
    fun `overrides collector if it's not set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.collectors.any { it is JavaMemoryCollector } }
    }

    @Test
    fun `does not override collector if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.addCollector(CustomMemoryCollector())
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.collectors.any { it is CustomMemoryCollector } }
    }

    private class CustomMainThreadChecker : IMainThreadChecker {
        override fun isMainThread(threadId: Long): Boolean = false
    }

    private class CustomMemoryCollector : ICollector {
        override fun setup() {}
        override fun collect(performanceCollectionData: PerformanceCollectionData) {}
    }

    private class CustomModulesLoader : IModulesLoader {
        override fun getOrLoadModules(): MutableMap<String, String>? = null
    }

    private class CustomEnvelopCache : IEnvelopeCache {
        override fun iterator(): MutableIterator<SentryEnvelope> = TODO()
        override fun store(envelope: SentryEnvelope, hint: Hint) = Unit
        override fun discard(envelope: SentryEnvelope) = Unit
    }

    private fun getTempPath(): String {
        val tempFile = Files.createTempDirectory("cache").toFile()
        tempFile.delete()

        // sanity check
        assertFalse(tempFile.exists())
        return tempFile.absolutePath
    }
}
