package io.sentry.android.core

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.ISentryClient
import io.sentry.Sentry
import io.sentry.Sentry.OptionsConfiguration
import io.sentry.SentryEnvelope
import io.sentry.SentryLevel
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.FATAL
import io.sentry.SentryOptions
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.Session
import io.sentry.ShutdownHookIntegration
import io.sentry.SpotlightIntegration
import io.sentry.SystemOutLogger
import io.sentry.UncaughtExceptionHandlerIntegration
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.fragment.FragmentLifecycleIntegration
import io.sentry.android.replay.ReplayIntegration
import io.sentry.android.timber.SentryTimberIntegration
import io.sentry.cache.IEnvelopeCache
import io.sentry.cache.PersistingOptionsObserver
import io.sentry.cache.PersistingOptionsObserver.ENVIRONMENT_FILENAME
import io.sentry.cache.PersistingOptionsObserver.OPTIONS_CACHE
import io.sentry.cache.PersistingOptionsObserver.RELEASE_FILENAME
import io.sentry.cache.PersistingScopeObserver
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.SCOPE_CACHE
import io.sentry.cache.PersistingScopeObserver.TRANSACTION_FILENAME
import io.sentry.test.applyTestOptions
import io.sentry.test.initForTest
import io.sentry.transport.NoOpEnvelopeCache
import io.sentry.util.StringUtils
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.N],
    shadows = [SentryShadowProcess::class]
)
class SentryAndroidTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {
        lateinit var shadowActivityManager: ShadowActivityManager

        fun initSut(
            context: Context? = null,
            autoInit: Boolean = false,
            logger: ILogger? = null,
            options: Sentry.OptionsConfiguration<SentryAndroidOptions>? = null
        ) {
            val metadata = Bundle().apply {
                putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
                putBoolean(ManifestMetadataReader.AUTO_INIT, autoInit)
            }
            val mockContext = context ?: ContextUtilsTestHelper.mockMetaData(metaData = metadata)
            when {
                logger != null -> initForTest(mockContext, logger)
                options != null -> initForTest(mockContext, options)
                else -> initForTest(mockContext)
            }
        }

        fun addAppExitInfo(
            reason: Int? = ApplicationExitInfo.REASON_ANR,
            timestamp: Long? = null,
            importance: Int? = null
        ) {
            val builder = ApplicationExitInfoBuilder.newBuilder()
            if (reason != null) {
                builder.setReason(reason)
            }
            if (timestamp != null) {
                builder.setTimestamp(timestamp)
            }
            if (importance != null) {
                builder.setImportance(importance)
            }
            val exitInfo = spy(builder.build()) {
                whenever(mock.traceInputStream).thenReturn(
                    """
"main" prio=5 tid=1 Blocked
  | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72a985e0 self=0xb400007cabc57380
  | sysTid=28941 nice=-10 cgrp=top-app sched=0/0 handle=0x7deceb74f8
  | state=S schedstat=( 324804784 183300334 997 ) utm=23 stm=8 core=3 HZ=100
  | stack=0x7ff93a9000-0x7ff93ab000 stackSize=8188KB
  | held mutexes=
  at io.sentry.samples.android.MainActivity${'$'}2.run(MainActivity.java:177)
  - waiting to lock <0x0d3a2f0a> (a java.lang.Object) held by thread 5
  at android.os.Handler.handleCallback(Handler.java:942)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:201)
  at android.os.Looper.loop(Looper.java:288)
  at android.app.ActivityThread.main(ActivityThread.java:7872)
  at java.lang.reflect.Method.invoke(Native method)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:548)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:936)

"perfetto_hprof_listener" prio=10 tid=7 Native (still starting up)
  | group="" sCount=1 ucsCount=0 flags=1 obj=0x0 self=0xb400007cabc5ab20
  | sysTid=28959 nice=-20 cgrp=top-app sched=0/0 handle=0x7b2021bcb0
  | state=S schedstat=( 72750 1679167 1 ) utm=0 stm=0 core=3 HZ=100
  | stack=0x7b20124000-0x7b20126000 stackSize=991KB
  | held mutexes=
  native: #00 pc 00000000000a20f4  /apex/com.android.runtime/lib64/bionic/libc.so (read+4) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  native: #01 pc 000000000001d840  /apex/com.android.art/lib64/libperfetto_hprof.so (void* std::__1::__thread_proxy<std::__1::tuple<std::__1::unique_ptr<std::__1::__thread_struct, std::__1::default_delete<std::__1::__thread_struct> >, ArtPlugin_Initialize::${'$'}_34> >(void*)+260) (BuildId: 525cc92a7dc49130157aeb74f6870364)
  native: #02 pc 00000000000b63b0  /apex/com.android.runtime/lib64/bionic/libc.so (__pthread_start(void*)+208) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  native: #03 pc 00000000000530b8  /apex/com.android.runtime/lib64/bionic/libc.so (__start_thread+64) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  (no managed stack frames)
                    """.trimIndent().byteInputStream()
                )
            }
            shadowActivityManager.addApplicationExitInfo(exitInfo)
        }
    }

    private val fixture = Fixture()
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        Sentry.close()
        AppStartMetrics.getInstance().clear()
        context = ApplicationProvider.getApplicationContext()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        fixture.shadowActivityManager = Shadow.extract(activityManager)
    }

    @Test
    fun `when auto-init is disabled and user calls init manually, SDK initializes`() {
        assertFalse(Sentry.isEnabled())

        fixture.initSut()

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when auto-init is disabled and user calls init manually with a logger, SDK initializes`() {
        assertFalse(Sentry.isEnabled())

        fixture.initSut(logger = mock())

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when auto-init is disabled and user calls init manually with configuration handler, options should be set`() {
        assertFalse(Sentry.isEnabled())

        var refOptions: SentryAndroidOptions? = null
        fixture.initSut {
            it.anrTimeoutIntervalMillis = 3000
            refOptions = it
        }

        assertEquals(3000, refOptions!!.anrTimeoutIntervalMillis)
        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `init won't throw exception`() {
        val logger = mock<ILogger>()

        fixture.initSut(autoInit = true, logger = logger)

        verify(logger, never()).log(eq(SentryLevel.FATAL), any<String>(), any())
    }

    @Test
    fun `set app start if provider is disabled`() {
        fixture.initSut(autoInit = true)

        // done by ActivityLifecycleIntegration so forcing it here
        AppStartMetrics.getInstance().apply {
            appStartType = AppStartMetrics.AppStartType.COLD
            appStartTimeSpan.apply {
                setStartedAt(1)
                setStoppedAt(1 + SystemClock.uptimeMillis())
            }
        }

        assertNotEquals(0, AppStartMetrics.getInstance().appStartTimeSpan.durationMs)
    }

    @Test
    fun `deduplicates fragment and timber integrations`() {
        var refOptions: SentryAndroidOptions? = null

        fixture.initSut(autoInit = true) {
            it.addIntegration(
                FragmentLifecycleIntegration(ApplicationProvider.getApplicationContext())
            )

            it.addIntegration(
                SentryTimberIntegration(minEventLevel = FATAL, minBreadcrumbLevel = DEBUG)
            )
            refOptions = it
        }

        assertEquals(refOptions!!.integrations.filterIsInstance<SentryTimberIntegration>().size, 1)
        val timberIntegration =
            refOptions!!.integrations.find { it is SentryTimberIntegration } as SentryTimberIntegration
        assertEquals(timberIntegration.minEventLevel, FATAL)
        assertEquals(timberIntegration.minBreadcrumbLevel, DEBUG)

        // fragment integration is not auto-installed in the test, since the context is not Application
        // but we just verify here that the single integration is preserved
        assertEquals(
            refOptions!!.integrations.filterIsInstance<FragmentLifecycleIntegration>().size,
            1
        )
    }

    @Test
    fun `AndroidEnvelopeCache is reset if the user disabled caching via cacheDirPath`() {
        var refOptions: SentryAndroidOptions? = null

        fixture.initSut {
            it.cacheDirPath = null

            refOptions = it
        }

        assertTrue { refOptions!!.envelopeDiskCache is NoOpEnvelopeCache }
    }

    @Test
    fun `envelopeCache remains unchanged if the user set their own IEnvelopCache impl`() {
        var refOptions: SentryAndroidOptions? = null

        fixture.initSut {
            it.cacheDirPath = null
            it.setEnvelopeDiskCache(CustomEnvelopCache())

            refOptions = it
        }

        assertTrue { refOptions!!.envelopeDiskCache is CustomEnvelopCache }
    }

    @Test
    fun `When initializing Sentry manually and changing both cache dir and dsn, the corresponding options should reflect that change`() {
        var options: SentryOptions? = null

        val mockContext = ContextUtilsTestHelper.createMockContext(true)
        val cacheDirPath = Files.createTempDirectory("new_cache").absolutePathString()
        initForTest(mockContext) {
            it.dsn = "https://key@sentry.io/123"
            it.cacheDirPath = cacheDirPath
            options = it
        }

        val dsnHash = StringUtils.calculateStringHash(options!!.dsn, options!!.logger)
        val expectedCacheDir = "$cacheDirPath/$dsnHash"
        assertEquals(expectedCacheDir, options!!.cacheDirPath)
        assertEquals(
            expectedCacheDir,
            (options!!.envelopeDiskCache as AndroidEnvelopeCache).directory.absolutePath
        )
    }

    @Test
    fun `init starts a session if auto session tracking is enabled and app is in foreground`() {
        initSentryWithForegroundImportance(true) { session: Session? ->
            assertNotNull(session)
        }
    }

    @Test
    fun `init does not start a session if auto session tracking is enabled but the app is in background`() {
        initSentryWithForegroundImportance(false) { session: Session? ->
            assertNull(session)
        }
    }

    @Test
    fun `init does not start a session if one is already running`() {
        val client = mock<ISentryClient>()
        whenever(client.isEnabled).thenReturn(true)

        initSentryWithForegroundImportance(true, { options ->
            options.addIntegration { hub, _ ->
                hub.bindClient(client)
                // usually done by LifecycleWatcher
                hub.startSession()
            }
        }) {}

        verify(client, times(1)).captureSession(any(), any())
    }

    @Test
    @Config(sdk = [26])
    fun `init starts session replay if app is in foreground`() {
        initSentryWithForegroundImportance(true) { _ ->
            assertTrue(Sentry.getCurrentHub().options.replayController.isRecording())
        }
    }

    @Test
    @Config(sdk = [26])
    fun `init does not start session replay if the app is in background`() {
        initSentryWithForegroundImportance(false) { _ ->
            assertFalse(Sentry.getCurrentHub().options.replayController.isRecording())
        }
    }

    @Test
    fun `When initializing Sentry a callback is added to application by appStartMetrics`() {
        val mockContext = ContextUtilsTestHelper.createMockContext(true)
        initForTest(mockContext) {
            it.dsn = "https://key@sentry.io/123"
        }
        verify(mockContext.applicationContext as Application).registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
    }

    private fun initSentryWithForegroundImportance(
        inForeground: Boolean,
        optionsConfig: (SentryAndroidOptions) -> Unit = {},
        callback: (session: Session?) -> Unit
    ) {
        Mockito.mockStatic(ContextUtils::class.java, Mockito.CALLS_REAL_METHODS).use { mockedContextUtils ->
            mockedContextUtils.`when`<Any> { ContextUtils.isForegroundImportance() }
                .thenReturn(inForeground)
            initForTest(context) { options ->
                options.release = "prod"
                options.dsn = "https://key@sentry.io/123"
                options.isEnableAutoSessionTracking = true
                options.experimental.sessionReplay.onErrorSampleRate = 1.0
                optionsConfig(options)
            }

            var session: Session? = null
            Sentry.getCurrentScopes().configureScope { scope ->
                session = scope.session
            }
            callback(session)
        }
    }

    @Test
    fun `init does not start a session by if auto session tracking is disabled`() {
        fixture.initSut { options ->
            options.isEnableAutoSessionTracking = false
        }
        Sentry.getCurrentScopes().withScope { scope ->
            assertNull(scope.session)
        }
    }

    @Test
    @Config(sdk = [30])
    fun `AnrV2 events get enriched with previously persisted scope and options data, the new data gets persisted after that`() {
        val cacheDir = tmpDir.newFolder().absolutePath
        fixture.addAppExitInfo(timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val asserted = AtomicBoolean(false)
        lateinit var options: SentryOptions

        fixture.initSut(context) {
            it.dsn = "https://key@sentry.io/123"
            it.cacheDirPath = cacheDir
            it.isDebug = true
            it.setLogger(SystemOutLogger())
            // beforeSend is called after event processors are applied, so we can assert here
            // against the enriched ANR event
            it.beforeSend = BeforeSendCallback { event, hint ->
                assertEquals("MainActivity", event.transaction)
                assertEquals("Debug!", event.breadcrumbs!![0].message)
                assertEquals("staging", event.environment)
                assertEquals("io.sentry.sample@2.0.0", event.release)
                asserted.set(true)
                null
            }

            // have to do it after the cacheDir is set to options, because it adds a dsn hash after
            prefillOptionsCache(it.cacheDirPath!!)
            prefillScopeCache(it.cacheDirPath!!)

            it.release = "io.sentry.sample@1.1.0+220"
            it.environment = "debug"
            // this is necessary to delay the AnrV2Integration processing to execute the configure
            // scope block below (otherwise it won't be possible as scopes is no-op before .init)
            it.executorService.submit {
                Sentry.configureScope { scope ->
                    // make sure the scope values changed to test that we're still using previously
                    // persisted values for the old ANR events
                    assertEquals("TestActivity", scope.transactionName)
                }
            }
            options = it
        }
        Sentry.configureScope {
            it.setTransaction("TestActivity")
            it.addBreadcrumb(Breadcrumb.error("Error!"))
        }
        await.withAlias("Failed because of BeforeSend callback above, but we swallow BeforeSend exceptions, hence the timeout")
            .untilTrue(asserted)

        // Execute all posted tasks
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // assert that persisted values have changed
        assertEquals(
            "TestActivity",
            PersistingScopeObserver.read(options, TRANSACTION_FILENAME, String::class.java)
        )
        assertEquals(
            "io.sentry.sample@1.1.0+220",
            PersistingOptionsObserver.read(options, RELEASE_FILENAME, String::class.java)
        )
    }

    @Test
    fun `init can remove all integrations`() {
        lateinit var optionsRef: SentryOptions
        fixture.initSut(context = mock<Application>()) { options ->
            optionsRef = options
            options.dsn = "https://key@sentry.io/123"
            assertEquals(21, options.integrations.size)
            options.integrations.removeAll {
                it is UncaughtExceptionHandlerIntegration ||
                    it is ShutdownHookIntegration ||
                    it is SendCachedEnvelopeIntegration ||
                    it is NdkIntegration ||
                    it is EnvelopeFileObserverIntegration ||
                    it is AppLifecycleIntegration ||
                    it is AnrIntegration ||
                    it is ActivityLifecycleIntegration ||
                    it is ActivityBreadcrumbsIntegration ||
                    it is CurrentActivityIntegration ||
                    it is UserInteractionIntegration ||
                    it is FragmentLifecycleIntegration ||
                    it is SentryTimberIntegration ||
                    it is AppComponentsBreadcrumbsIntegration ||
                    it is SystemEventsBreadcrumbsIntegration ||
                    it is NetworkBreadcrumbsIntegration ||
                    it is TempSensorBreadcrumbsIntegration ||
                    it is PhoneStateBreadcrumbsIntegration ||
                    it is SpotlightIntegration ||
                    it is ReplayIntegration
            }
        }
        assertEquals(0, optionsRef.integrations.size)
    }

    @Test
    fun `init backfills sdk init and app start time`() {
        AppStartMetrics.getInstance().clear()
        SentryShadowProcess.setStartUptimeMillis(42)

        fixture.initSut(context = mock<Application>()) { options ->
            options.dsn = "https://key@sentry.io/123"
            options.isEnablePerformanceV2 = true
        }
        assertEquals(42, AppStartMetrics.getInstance().appStartTimeSpan.startUptimeMs)
        assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasStarted())
    }

    @Test
    fun `init does not backfill sdk init and app start times if already set`() {
        AppStartMetrics.getInstance().clear()
        AppStartMetrics.getInstance().sdkInitTimeSpan.setStartedAt(99)
        AppStartMetrics.getInstance().appStartTimeSpan.setStartedAt(99)

        SentryShadowProcess.setStartUptimeMillis(42)
        fixture.initSut(context = mock<Application>()) { options ->
            options.dsn = "https://key@sentry.io/123"
            options.isEnablePerformanceV2 = true
        }

        assertEquals(99, AppStartMetrics.getInstance().sdkInitTimeSpan.startUptimeMs)
        assertEquals(99, AppStartMetrics.getInstance().appStartTimeSpan.startUptimeMs)
    }

    @Test
    fun `if the config options block throws still intializes android event processors`() {
        lateinit var optionsRef: SentryOptions
        fixture.initSut(context = mock<Application>()) { options ->
            optionsRef = options
            options.dsn = "https://key@sentry.io/123"
            throw RuntimeException("Boom!")
        }

        assertTrue(optionsRef.eventProcessors.any { it is DefaultAndroidEventProcessor })
        assertTrue(optionsRef.eventProcessors.any { it is AnrV2EventProcessor })
    }

    private fun prefillScopeCache(cacheDir: String) {
        val scopeDir = File(cacheDir, SCOPE_CACHE).also { it.mkdirs() }
        File(scopeDir, BREADCRUMBS_FILENAME).writeText(
            """
            [{
              "timestamp": "2009-11-16T01:08:47.000Z",
              "message": "Debug!",
              "type": "debug",
              "level": "debug"
            }]
            """.trimIndent()
        )
        File(scopeDir, TRANSACTION_FILENAME).writeText("\"MainActivity\"")
    }

    private fun prefillOptionsCache(cacheDir: String) {
        val optionsDir = File(cacheDir, OPTIONS_CACHE).also { it.mkdirs() }
        File(optionsDir, RELEASE_FILENAME).writeText("\"io.sentry.sample@2.0.0\"")
        File(optionsDir, ENVIRONMENT_FILENAME).writeText("\"staging\"")
    }

    private class CustomEnvelopCache : IEnvelopeCache {
        override fun iterator(): MutableIterator<SentryEnvelope> = TODO()
        override fun store(envelope: SentryEnvelope, hint: Hint) = Unit
        override fun discard(envelope: SentryEnvelope) = Unit
    }
}

fun initForTest(context: Context, optionsConfiguration: OptionsConfiguration<SentryAndroidOptions>) {
    SentryAndroid.init(context) {
        applyTestOptions(it)
        optionsConfiguration.configure(it)
    }
}

fun initForTest(context: Context, logger: ILogger) {
    SentryAndroid.init(context, logger)
}

fun initForTest(context: Context) {
    SentryAndroid.init(context)
}
