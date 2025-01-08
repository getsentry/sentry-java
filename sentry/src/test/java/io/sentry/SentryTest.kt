package io.sentry

import io.sentry.SentryOptions.ProfilesSamplerCallback
import io.sentry.SentryOptions.TracesSamplerCallback
import io.sentry.backpressure.BackpressureMonitor
import io.sentry.backpressure.NoOpBackpressureMonitor
import io.sentry.cache.EnvelopeCache
import io.sentry.cache.IEnvelopeCache
import io.sentry.internal.debugmeta.IDebugMetaLoader
import io.sentry.internal.debugmeta.ResourcesDebugMetaLoader
import io.sentry.internal.modules.CompositeModulesLoader
import io.sentry.internal.modules.IModulesLoader
import io.sentry.internal.modules.NoOpModulesLoader
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryThread
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.PlatformTestManipulator
import io.sentry.util.thread.IMainThreadChecker
import io.sentry.util.thread.MainThreadChecker
import org.awaitility.kotlin.await
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryTest {

    private val dsn = "http://key@localhost/proj"

    @get:Rule
    val tmpDir = TemporaryFolder()

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `init multiple times calls hub close with isRestarting true`() {
        val hub = mock<IHub>()
        Sentry.init {
            it.dsn = dsn
        }
        Sentry.setCurrentHub(hub)
        Sentry.init {
            it.dsn = dsn
        }
        verify(hub).close(eq(true))
    }

    @Test
    fun `close calls hub close with isRestarting false`() {
        val hub = mock<IHub>()
        Sentry.init {
            it.dsn = dsn
        }
        Sentry.setCurrentHub(hub)
        Sentry.close()
        verify(hub).close(eq(false))
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
    fun `getCacheDirPathWithoutDsn should be created at initialization`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
            it.cacheDirPath = getTempPath()
            sentryOptions = it
        }

        val cacheDirPathWithoutDsn = sentryOptions!!.cacheDirPathWithoutDsn!!
        val file = File(cacheDirPathWithoutDsn)
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
        verify(logger).log(
            eq(SentryLevel.WARNING),
            eq("Sentry has been already initialized. Previous configuration will be overwritten.")
        )
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
        verify(logger).log(
            eq(SentryLevel.WARNING),
            eq("Sentry has been already initialized. Previous configuration will be overwritten.")
        )
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
    fun `initializes Sentry with enabled=false, thus disabling Sentry even if dsn is set`() {
        Sentry.init {
            it.isEnabled = false
            it.dsn = "http://key@localhost/proj"
        }

        Sentry.setTag("none", "shouldNotExist")

        var value: String? = null
        Sentry.getCurrentHub().configureScope {
            value = it.tags[value]
        }
        assertTrue(Sentry.getCurrentHub() is NoOpHub)
        assertNull(value)
    }

    @Test
    fun `initializes Sentry with enabled=false, thus disabling Sentry even if dsn is null`() {
        Sentry.init {
            it.isEnabled = false
        }

        Sentry.setTag("none", "shouldNotExist")

        var value: String? = null
        Sentry.getCurrentHub().configureScope {
            value = it.tags[value]
        }
        assertTrue(Sentry.getCurrentHub() is NoOpHub)
        assertNull(value)
    }

    @Test
    fun `initializes Sentry with dsn = null, throwing IllegalArgumentException`() {
        val exception =
            assertThrows(java.lang.IllegalArgumentException::class.java) { Sentry.init() }
        assertEquals(
            "DSN is required. Use empty string or set enabled to false in SentryOptions to disable SDK.",
            exception.message
        )
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

        val transaction = Sentry.startTransaction("name", "op", "desc", TransactionOptions())
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
    fun `only old profiles in profilingTracesDirPath should be cleared when profiling is enabled`() {
        val tempPath = getTempPath()
        val options = SentryOptions().also {
            it.dsn = dsn
            it.cacheDirPath = tempPath
        }
        val dir = File(options.profilingTracesDirPath!!)
        val oldProfile = File(dir, "oldProfile")
        val newProfile = File(dir, "newProfile")

        // Create all files
        dir.mkdirs()
        oldProfile.createNewFile()
        newProfile.createNewFile()
        // Make the old profile look like it's created earlier
        oldProfile.setLastModified(10000)
        // Make the new profile look like it's created later
        newProfile.setLastModified(System.currentTimeMillis() + 10000)

        // Assert both file exist
        assertTrue(oldProfile.exists())
        assertTrue(newProfile.exists())

        Sentry.init {
            it.dsn = dsn
            it.profilesSampleRate = 1.0
            it.cacheDirPath = tempPath
            it.executorService = ImmediateExecutorService()
        }

        // Assert only the new profile exists
        assertFalse(oldProfile.exists())
        assertTrue(newProfile.exists())
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
    fun `overrides debug meta loader if it's not set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.debugMetaLoader is ResourcesDebugMetaLoader }
    }

    @Test
    fun `does not override debug meta loader if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.setDebugMetaLoader(CustomDebugMetaLoader())
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.debugMetaLoader is CustomDebugMetaLoader }
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

        assertTrue { sentryOptions!!.performanceCollectors.any { it is JavaMemoryCollector } }
    }

    @Test
    fun `does not override collector if it's already set`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.addPerformanceCollector(CustomMemoryCollector())
            sentryOptions = it
        }

        assertTrue { sentryOptions!!.performanceCollectors.any { it is CustomMemoryCollector } }
    }

    @Test
    fun `init does not throw on executor shut down`() {
        val logger = mock<ILogger>()

        Sentry.init {
            it.dsn = dsn
            it.profilesSampleRate = 1.0
            it.cacheDirPath = getTempPath()
            it.setLogger(logger)
            it.executorService.close(0)
            it.isDebug = true
        }
        verify(logger).log(eq(SentryLevel.ERROR), eq("Failed to call the executor. Old profiles will not be deleted. Did you call Sentry.close()?"), any())
    }

    @Test
    fun `reportFullyDisplayed calls hub reportFullyDisplayed`() {
        val hub = mock<IHub>()
        Sentry.init {
            it.dsn = dsn
        }
        Sentry.setCurrentHub(hub)
        Sentry.reportFullyDisplayed()
        verify(hub).reportFullyDisplayed()
    }

    @Test
    fun `reportFullDisplayed calls reportFullyDisplayed`() {
        val hub = mock<IHub>()
        Sentry.init {
            it.dsn = dsn
        }
        Sentry.setCurrentHub(hub)
        Sentry.reportFullDisplayed()
        verify(hub).reportFullyDisplayed()
    }

    @Test
    fun `ignores executorService if it is closed`() {
        var sentryOptions: SentryOptions? = null
        val executorService = mock<ISentryExecutorService>()
        whenever(executorService.isClosed).thenReturn(true)

        Sentry.init {
            it.dsn = dsn
            it.executorService = executorService
            sentryOptions = it
        }

        assertNotEquals(executorService, sentryOptions!!.executorService)
    }

    @Test
    fun `accept executorService if it is not closed`() {
        var sentryOptions: SentryOptions? = null
        val executorService = mock<ISentryExecutorService>()
        whenever(executorService.isClosed).thenReturn(false)

        Sentry.init {
            it.dsn = dsn
            it.executorService = executorService
            sentryOptions = it
        }

        assertEquals(executorService, sentryOptions!!.executorService)
    }

    @Test
    fun `init notifies option observers`() {
        val optionsObserver = InMemoryOptionsObserver()

        Sentry.init {
            it.dsn = dsn

            it.executorService = ImmediateExecutorService()

            it.addOptionsObserver(optionsObserver)

            it.release = "io.sentry.sample@1.1.0+220"
            it.proguardUuid = "uuid"
            it.dist = "220"
            it.sdkVersion = SdkVersion("sentry.java.android", "6.13.0")
            it.environment = "debug"
            it.setTag("one", "two")
            it.sessionReplay.onErrorSampleRate = 0.5
        }

        assertEquals("io.sentry.sample@1.1.0+220", optionsObserver.release)
        assertEquals("debug", optionsObserver.environment)
        assertEquals("220", optionsObserver.dist)
        assertEquals("uuid", optionsObserver.proguardUuid)
        assertEquals(mapOf("one" to "two"), optionsObserver.tags)
        assertEquals(SdkVersion("sentry.java.android", "6.13.0"), optionsObserver.sdkVersion)
        assertEquals(0.5, optionsObserver.replayErrorSampleRate)
    }

    @Test
    fun `if there is work enqueued, init notifies options observers after that work is done`() {
        val optionsObserver = InMemoryOptionsObserver().apply {
            setRelease("io.sentry.sample@2.0.0")
            setEnvironment("production")
        }
        val triggered = AtomicBoolean(false)

        Sentry.init {
            it.dsn = dsn

            it.addOptionsObserver(optionsObserver)

            it.release = "io.sentry.sample@1.1.0+220"
            it.environment = "debug"

            it.executorService.submit {
                // here the values should be still old. Sentry.init will submit another runnable
                // to notify the options observers, but because the executor is single-threaded, the
                // work will be enqueued and the observers will be notified after current work is
                // finished, ensuring that even if something is using the options observer from a
                // different thread, it will still use the old values.
                Thread.sleep(1000L)
                assertEquals("io.sentry.sample@2.0.0", optionsObserver.release)
                assertEquals("production", optionsObserver.environment)
                triggered.set(true)
            }
        }

        await.untilTrue(triggered)
        assertEquals("io.sentry.sample@1.1.0+220", optionsObserver.release)
        assertEquals("debug", optionsObserver.environment)
    }

    @Test
    fun `init finalizes previous session`() {
        lateinit var previousSessionFile: File

        Sentry.init {
            it.dsn = dsn
            it.isDebug = true
            it.setLogger(SystemOutLogger())

            it.release = "io.sentry.sample@2.0"
            it.cacheDirPath = tmpDir.newFolder().absolutePath

            it.executorService = ImmediateExecutorService()

            previousSessionFile = EnvelopeCache.getPreviousSessionFile(it.cacheDirPath!!)
            previousSessionFile.parentFile.mkdirs()
            it.serializer.serialize(
                Session(null, null, "release", "io.sentry.samples@2.0"),
                previousSessionFile.bufferedWriter()
            )
            assertEquals(
                "release",
                it.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)!!.environment
            )

            it.addIntegration { hub, _ ->
                // this is just a hack to trigger the previousSessionFlush latch, so the finalizer
                // does not time out waiting. We have to do it as integration, because this is where
                // the hub is already initialized
                hub.startSession()
            }
        }

        assertFalse(previousSessionFile.exists())
    }

    @Test
    fun `if there is work enqueued, init finalizes previous session after that work is done`() {
        lateinit var previousSessionFile: File
        val triggered = AtomicBoolean(false)

        Sentry.init {
            it.dsn = dsn

            it.release = "io.sentry.sample@2.0"
            it.cacheDirPath = tmpDir.newFolder().absolutePath

            previousSessionFile = EnvelopeCache.getPreviousSessionFile(it.cacheDirPath!!)
            previousSessionFile.parentFile.mkdirs()
            it.serializer.serialize(
                Session(null, null, "release", "io.sentry.sample@1.0"),
                previousSessionFile.bufferedWriter()
            )

            it.executorService.submit {
                // here the previous session should still exist. Sentry.init will submit another runnable
                // to finalize the previous session, but because the executor is single-threaded, the
                // work will be enqueued and the previous session will be finalized after current work is
                // finished, ensuring that even if something is using the previous session from a
                // different thread, it will still be able to access it.
                Thread.sleep(1000L)
                val session = it.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
                assertEquals("io.sentry.sample@1.0", session!!.release)
                assertEquals("release", session.environment)
                triggered.set(true)
            }
        }

        // to trigger previous session flush
        Sentry.startSession()

        await.untilTrue(triggered)
        assertFalse(previousSessionFile.exists())
    }

    @Test
    fun `captureCheckIn gets forwarded to client`() {
        Sentry.init { it.dsn = dsn }

        val client = mock<ISentryClient>()
        Sentry.getCurrentHub().bindClient(client)

        val checkIn = CheckIn("some_slug", CheckInStatus.OK)
        Sentry.captureCheckIn(checkIn)

        verify(client).captureCheckIn(
            argThat {
                checkInId == checkIn.checkInId
            },
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `if send modules is false, uses NoOpModulesLoader`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
            it.isSendModules = false
            sentryOptions = it
        }

        assertIs<NoOpModulesLoader>(sentryOptions?.modulesLoader)
    }

    @Test
    fun `if Sentry is disabled through options with scope callback is executed`() {
        Sentry.init {
            it.isEnabled = false
        }

        val scopeCallback = mock<ScopeCallback>()

        Sentry.withScope(scopeCallback)

        verify(scopeCallback).run(any())
    }

    @Test
    fun `if Sentry is not initialized with scope callback is executed`() {
        val scopeCallback = mock<ScopeCallback>()

        Sentry.withScope(scopeCallback)

        verify(scopeCallback).run(any())
    }

    @Test
    fun `getSpan calls hub getSpan`() {
        val hub = mock<IHub>()
        Sentry.init({
            it.dsn = dsn
        }, false)
        Sentry.setCurrentHub(hub)
        Sentry.getSpan()
        verify(hub).span
    }

    @Test
    fun `getSpan calls returns root span if globalhub mode is enabled on Android`() {
        PlatformTestManipulator.pretendIsAndroid(true)
        Sentry.init({
            it.dsn = dsn
            it.enableTracing = true
            it.sampleRate = 1.0
        }, true)

        val transaction = Sentry.startTransaction("name", "op-root", TransactionOptions().also { it.isBindToScope = true })
        transaction.startChild("op-child")

        val span = Sentry.getSpan()!!
        assertEquals("op-root", span.operation)
        PlatformTestManipulator.pretendIsAndroid(false)
    }

    @Test
    fun `getSpan calls returns child span if globalhub mode is enabled, but the platform is not Android`() {
        PlatformTestManipulator.pretendIsAndroid(false)
        Sentry.init({
            it.dsn = dsn
            it.enableTracing = true
            it.sampleRate = 1.0
        }, false)

        val transaction = Sentry.startTransaction("name", "op-root", TransactionOptions().also { it.isBindToScope = true })
        transaction.startChild("op-child")

        val span = Sentry.getSpan()!!
        assertEquals("op-child", span.operation)
    }

    @Test
    fun `getSpan calls returns child span if globalhub mode is disabled`() {
        Sentry.init({
            it.dsn = dsn
            it.enableTracing = true
            it.sampleRate = 1.0
        }, false)

        val transaction = Sentry.startTransaction("name", "op-root", TransactionOptions().also { it.isBindToScope = true })
        transaction.startChild("op-child")

        val span = Sentry.getSpan()!!
        assertEquals("op-child", span.operation)
    }

    @Test
    fun `backpressure monitor is a NoOp if handling is disabled`() {
        var sentryOptions: SentryOptions? = null
        Sentry.init {
            it.dsn = dsn
            it.isEnableBackpressureHandling = false
            sentryOptions = it
        }
        assertIs<NoOpBackpressureMonitor>(sentryOptions?.backpressureMonitor)
    }

    @Test
    fun `backpressure monitor is set if handling is enabled`() {
        var sentryOptions: SentryOptions? = null

        Sentry.init {
            it.dsn = dsn
            it.isEnableBackpressureHandling = true
            sentryOptions = it
        }
        assertIs<BackpressureMonitor>(sentryOptions?.backpressureMonitor)
    }

    @Test
    fun `init calls samplers if isEnableAppStartProfiling is true`() {
        val mockSampleTracer = mock<TracesSamplerCallback>()
        val mockProfilesSampler = mock<ProfilesSamplerCallback>()
        Sentry.init {
            it.dsn = dsn
            it.enableTracing = true
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 1.0
            it.tracesSampler = mockSampleTracer
            it.profilesSampler = mockProfilesSampler
            it.executorService = ImmediateExecutorService()
            it.cacheDirPath = getTempPath()
        }
        // Samplers are called with isForNextAppStart flag set to true
        verify(mockSampleTracer).sample(
            check {
                assertEquals("app.launch", it.transactionContext.name)
                assertEquals("profile", it.transactionContext.operation)
                assertTrue(it.transactionContext.isForNextAppStart)
            }
        )
        verify(mockProfilesSampler).sample(
            check {
                assertEquals("app.launch", it.transactionContext.name)
                assertEquals("profile", it.transactionContext.operation)
                assertTrue(it.transactionContext.isForNextAppStart)
            }
        )
    }

    @Test
    fun `init calls app start profiling samplers in the background`() {
        val mockSampleTracer = mock<TracesSamplerCallback>()
        val mockProfilesSampler = mock<ProfilesSamplerCallback>()
        Sentry.init {
            it.dsn = dsn
            it.enableTracing = true
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 1.0
            it.tracesSampler = mockSampleTracer
            it.profilesSampler = mockProfilesSampler
            it.executorService = NoOpSentryExecutorService.getInstance()
            it.cacheDirPath = getTempPath()
        }
        // Samplers are called with isForNextAppStart flag set to true
        verify(mockSampleTracer, never()).sample(any())
        verify(mockProfilesSampler, never()).sample(any())
    }

    @Test
    fun `init does not call app start profiling samplers if cache dir is null`() {
        val mockSampleTracer = mock<TracesSamplerCallback>()
        val mockProfilesSampler = mock<ProfilesSamplerCallback>()
        Sentry.init {
            it.dsn = dsn
            it.enableTracing = true
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 1.0
            it.tracesSampler = mockSampleTracer
            it.profilesSampler = mockProfilesSampler
            it.executorService = NoOpSentryExecutorService.getInstance()
            it.cacheDirPath = null
        }
        // Samplers are called with isForNextAppStart flag set to true
        verify(mockSampleTracer, never()).sample(any())
        verify(mockProfilesSampler, never()).sample(any())
    }

    @Test
    fun `init does not call app start profiling samplers if enableTracing is false`() {
        val logger = mock<ILogger>()
        val mockTraceSampler = mock<TracesSamplerCallback>()
        val mockProfilesSampler = mock<ProfilesSamplerCallback>()
        Sentry.init {
            it.dsn = dsn
            it.enableTracing = false
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 1.0
            it.tracesSampler = mockTraceSampler
            it.profilesSampler = mockProfilesSampler
            it.executorService = ImmediateExecutorService()
            it.cacheDirPath = getTempPath()
            it.isDebug = true
            it.setLogger(logger)
        }
        verify(logger).log(eq(SentryLevel.INFO), eq("Tracing is disabled and app start profiling will not start."))
        verify(mockTraceSampler, never()).sample(any())
        verify(mockProfilesSampler, never()).sample(any())
    }

    @Test
    fun `init deletes app start profiling config`() {
        val path = getTempPath()
        File(path).mkdirs()
        val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
        appStartProfilingConfigFile.createNewFile()
        assertTrue(appStartProfilingConfigFile.exists())
        Sentry.init {
            it.dsn = dsn
            it.executorService = ImmediateExecutorService()
            it.cacheDirPath = path
        }
        assertFalse(appStartProfilingConfigFile.exists())
    }

    @Test
    fun `init creates app start profiling config if isEnableAppStartProfiling and enableTracing is true`() {
        val path = getTempPath()
        File(path).mkdirs()
        val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
        appStartProfilingConfigFile.createNewFile()
        assertTrue(appStartProfilingConfigFile.exists())
        Sentry.init {
            it.dsn = dsn
            it.cacheDirPath = path
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 1.0
            it.enableTracing = true
            it.executorService = ImmediateExecutorService()
        }
        assertTrue(appStartProfilingConfigFile.exists())
    }

    @Test
    fun `init saves SentryAppStartProfilingOptions to disk`() {
        var options = SentryOptions()
        val path = getTempPath()
        Sentry.init {
            it.dsn = dsn
            it.cacheDirPath = path
            it.enableTracing = true
            it.tracesSampleRate = 0.5
            it.isEnableAppStartProfiling = true
            it.profilesSampleRate = 0.2
            it.executorService = ImmediateExecutorService()
            options = it
        }
        val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
        assertTrue(appStartProfilingConfigFile.exists())
        val appStartOption =
            JsonSerializer(options).deserialize(FileReader(appStartProfilingConfigFile), SentryAppStartProfilingOptions::class.java)
        assertNotNull(appStartOption)
        assertEquals(0.5, appStartOption.traceSampleRate)
        assertEquals(0.2, appStartOption.profileSampleRate)
        assertTrue(appStartOption.isProfilingEnabled)
    }

    @Test
    fun `metrics calls hub getMetrics`() {
        val hub = mock<IHub>()
        Sentry.init({
            it.dsn = dsn
        }, false)
        Sentry.setCurrentHub(hub)

        Sentry.metrics()
        verify(hub).metrics()
    }

    private class InMemoryOptionsObserver : IOptionsObserver {
        var release: String? = null
            private set
        var environment: String? = null
            private set
        var proguardUuid: String? = null
            private set
        var sdkVersion: SdkVersion? = null
            private set
        var dist: String? = null
            private set
        var tags: Map<String, String> = mapOf()
            private set
        var replayErrorSampleRate: Double? = null
            private set

        override fun setRelease(release: String?) {
            this.release = release
        }

        override fun setEnvironment(environment: String?) {
            this.environment = environment
        }

        override fun setProguardUuid(proguardUuid: String?) {
            this.proguardUuid = proguardUuid
        }

        override fun setSdkVersion(sdkVersion: SdkVersion?) {
            this.sdkVersion = sdkVersion
        }

        override fun setDist(dist: String?) {
            this.dist = dist
        }

        override fun setTags(tags: MutableMap<String, String>) {
            this.tags = tags
        }

        override fun setReplayErrorSampleRate(replayErrorSampleRate: Double?) {
            this.replayErrorSampleRate = replayErrorSampleRate
        }
    }

    private class CustomMainThreadChecker : IMainThreadChecker {
        override fun isMainThread(threadId: Long): Boolean = false
        override fun isMainThread(thread: Thread): Boolean = false
        override fun isMainThread(): Boolean = false
        override fun isMainThread(sentryThread: SentryThread): Boolean = false
    }

    private class CustomMemoryCollector :
        IPerformanceSnapshotCollector {
        override fun setup() {}
        override fun collect(performanceCollectionData: PerformanceCollectionData) {}
    }

    private class CustomModulesLoader : IModulesLoader {
        override fun getOrLoadModules(): MutableMap<String, String>? = null
    }

    private class CustomDebugMetaLoader : IDebugMetaLoader {
        override fun loadDebugMeta(): List<Properties>? = null
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
