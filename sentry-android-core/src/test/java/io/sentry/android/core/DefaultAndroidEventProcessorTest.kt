package io.sentry.android.core

import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DiagnosticLogger
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint.SENTRY_DART_SDK_NAME
import io.sentry.android.core.DefaultAndroidEventProcessor.EMULATOR
import io.sentry.android.core.DefaultAndroidEventProcessor.KERNEL_VERSION
import io.sentry.android.core.DefaultAndroidEventProcessor.ROOTED
import io.sentry.android.core.DefaultAndroidEventProcessor.SIDE_LOADED
import io.sentry.android.core.internal.util.CpuInfoUtils
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryThread
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.getCtor
import io.sentry.util.HintUtils
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DefaultAndroidEventProcessorTest {
    private lateinit var context: Context

    private val className = "io.sentry.android.core.DefaultAndroidEventProcessor"
    private val ctorTypes =
        arrayOf(Context::class.java, BuildInfoProvider::class.java, SentryAndroidOptions::class.java)

    init {
        Locale.setDefault(Locale.US)
    }

    private class Fixture {
        val buildInfo = mock<BuildInfoProvider>()
        val options = SentryAndroidOptions().apply {
            isDebug = true
            setLogger(mock())
            sdkVersion = SdkVersion("test", "1.2.3")
        }

        val hub: IHub = mock<IHub>()

        lateinit var sentryTracer: SentryTracer

        fun getSut(context: Context): DefaultAndroidEventProcessor {
            whenever(hub.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("", ""), hub)
            return DefaultAndroidEventProcessor(context, buildInfo, options)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        AppState.getInstance().resetInstance()
    }

    @Test
    fun `when instance is created, application context reference is stored`() {
        val sut = fixture.getSut(context)

        assertEquals(sut.context, context)
    }

    @Test
    fun `when null context is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(null, null, mock<SentryAndroidOptions>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null logger is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(mock<Context>(), null, mock<SentryAndroidOptions>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null options is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(mock<Context>(), mock<BuildInfoProvider>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null buildInfo is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(null, mock<BuildInfoProvider>(), mock<SentryAndroidOptions>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `When Event and hint is not Cached, data should be applied`() {
        whenever(fixture.buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            assertNotNull(it.contexts.app)
            assertNotNull(it.dist)

            // assert adds permissions as unknown
            val permissions = it.contexts.app!!.permissions
            assertNotNull(permissions)
        }
    }

    @Test
    fun `when Android version is below JELLY_BEAN, does not add permissions`() {
        whenever(fixture.buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            // assert adds permissions
            val unknown = it.contexts.app!!.permissions
            assertNull(unknown)
        }
    }

    @Test
    fun `When Transaction and hint is not Cached, data should be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(
            sut.process(
                SentryTransaction(fixture.sentryTracer),
                Hint()
            )
        ) {
            assertNotNull(it.contexts.app)
            assertNotNull(it.dist)
        }
    }

    @Test
    fun `Current and Main should be true if it comes from main thread`() {
        val sut = fixture.getSut(context)

        val sentryThread = SentryThread().apply {
            id = Looper.getMainLooper().thread.id
        }
        val event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }

        assertNotNull(sut.process(event, Hint())) {
            assertNotNull(it.threads) { threads ->
                assertTrue(threads.first().isCurrent == true)
                assertTrue(threads.first().isMain == true)
            }
        }
    }

    @Test
    fun `Current should be false if it its not the main thread`() {
        val sut = fixture.getSut(context)

        val event = SentryEvent().apply {
            threads = mutableListOf(
                SentryThread().apply {
                    id = 10L
                }
            )
        }

        assertNotNull(sut.process(event, Hint())) {
            assertNotNull(it.threads) { threads ->
                assertFalse(threads.first().isCurrent == true)
                assertFalse(threads.first().isMain == true)
            }
        }
    }

    @Test
    fun `Current should remain true`() {
        val sut = fixture.getSut(context)

        val event = SentryEvent().apply {
            threads = mutableListOf(
                SentryThread().apply {
                    id = 10L
                    isCurrent = true
                }
            )
        }

        assertNotNull(sut.process(event, Hint())) {
            assertNotNull(it.threads) { threads ->
                assertTrue(threads.first().isCurrent == true)
            }
        }
    }

    @Test
    fun `When Event and hint is Cached, data should not be applied`() {
        val sut = fixture.getSut(context)

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        assertNotNull(sut.process(SentryEvent(), hints)) {
            assertNull(it.contexts.app)
            assertNull(it.debugMeta)
            assertNull(it.dist)
        }
    }

    @Test
    fun `When Transaction and hint is Cached, data should not be applied`() {
        val sut = fixture.getSut(context)

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), hints)) {
            assertNull(it.contexts.app)
            assertNull(it.dist)
        }
    }

    @Test
    fun `When Event and hint is Cached, userId is applied anyway`() {
        val sut = fixture.getSut(context)
        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        assertNotNull(sut.process(SentryEvent(), hints)) {
            assertNotNull(it.user)
        }
    }

    @Test
    fun `When Transaction and hint is Cached, userId is applied anyway`() {
        val sut = fixture.getSut(context)

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), hints)) {
            assertNotNull(it.user)
        }
    }

    @Test
    fun `When user with id is already set, do not overwrite it`() {
        val sut = fixture.getSut(context)

        val user = User().apply {
            id = "user-id"
        }
        val event = SentryEvent().apply {
            setUser(user)
        }

        assertNotNull(sut.process(event, Hint())) {
            assertNotNull(it.user)
            assertSame(user, it.user)
        }
    }

    @Test
    fun `When user without id is set, user id is applied`() {
        val sut = fixture.getSut(context)

        val event = SentryEvent().apply {
            user = User()
        }

        assertNotNull(sut.process(event, Hint())) {
            assertNotNull(it.user)
            assertNotNull(it.user!!.id)
        }
    }

    @Test
    fun `Executor service should be called on ctor`() {
        val sut = fixture.getSut(context)

        val contextData = sut.contextData.get()

        assertNotNull(contextData)
        assertNotNull(contextData[ROOTED])
        assertNotNull(contextData[KERNEL_VERSION])
        assertNotNull(contextData[EMULATOR])
        assertNotNull(contextData[SIDE_LOADED])
    }

    @Test
    fun `Processor won't throw exception`() {
        val sut = fixture.getSut(context)

        sut.process(SentryEvent(), Hint())

        verify(
            (fixture.options.logger as DiagnosticLogger).logger,
            never()
        )!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `Processor won't throw exception when theres a hint`() {
        val processor =
            DefaultAndroidEventProcessor(context, fixture.buildInfo, mock(), fixture.options)

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        processor.process(SentryEvent(), hints)

        verify(
            (fixture.options.logger as DiagnosticLogger).logger,
            never()
        )!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `When event is processed, sideLoaded info should be set`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            assertNotNull(it.getTag("isSideLoaded"))
        }
    }

    @Test
    fun `When event already has OS, add OS with custom key`() {
        val sut = fixture.getSut(context)

        val osLinux = OperatingSystem().apply {
            name = " Linux "
        }
        val event = SentryEvent().apply {
            contexts.setOperatingSystem(osLinux)
        }

        assertNotNull(sut.process(event, Hint())) {
            assertSame(osLinux, (it.contexts["os_linux"] as OperatingSystem))
            assertEquals("Android", it.contexts.operatingSystem!!.name)
        }
    }

    @Test
    fun `When event already has OS, add OS with generated key if no name`() {
        val sut = fixture.getSut(context)

        val osNoName = OperatingSystem().apply {
            version = "1.0"
        }
        val event = SentryEvent().apply {
            contexts.setOperatingSystem(osNoName)
        }

        assertNotNull(sut.process(event, Hint())) {
            assertSame(osNoName, (it.contexts["os_1"] as OperatingSystem))
            assertEquals("Android", it.contexts.operatingSystem!!.name)
        }
    }

    @Test
    fun `When hint is Cached, memory data should not be applied`() {
        val sut = fixture.getSut(context)

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        assertNotNull(sut.process(SentryEvent(), hints)) {
            assertNull(it.contexts.device!!.freeMemory)
            assertNull(it.contexts.device!!.isLowMemory)
        }
    }

    @Test
    fun `When hint is not Cached, memory data should be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            assertNotNull(it.contexts.device!!.freeMemory)
            assertNotNull(it.contexts.device!!.isLowMemory)
        }
    }

    @Test
    fun `Device's context is set on transactions`() {
        val sut = fixture.getSut(context)

        assertNotNull(
            sut.process(
                SentryTransaction(fixture.sentryTracer),
                Hint()
            )
        ) {
            assertNotNull(it.contexts.device)
        }
    }

    @Test
    fun `Device's OS is set on transactions`() {
        val sut = fixture.getSut(context)

        assertNotNull(
            sut.process(
                SentryTransaction(fixture.sentryTracer),
                Hint()
            )
        ) {
            assertNotNull(it.contexts.operatingSystem)
        }
    }

    @Test
    fun `Transaction do not set device's context that requires heavy work`() {
        val sut = fixture.getSut(context)

        assertNotNull(
            sut.process(
                SentryTransaction(fixture.sentryTracer),
                Hint()
            )
        ) {
            val device = it.contexts.device!!
            assertNull(device.batteryLevel)
            assertNull(device.isCharging)
            assertNull(device.batteryTemperature)
            assertNull(device.isOnline)
            assertNull(device.freeMemory)
            assertNull(device.isLowMemory)
            assertNull(device.storageSize)
            assertNull(device.freeStorage)
            assertNull(device.externalFreeStorage)
            assertNull(device.externalStorageSize)
            assertNull(device.connectionType)
        }
    }

    @Test
    fun `Event sets device's context that requires heavy work`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val device = it.contexts.device!!
            assertNotNull(device.freeMemory)
            assertNotNull(device.isLowMemory)
            assertNotNull(device.storageSize)
            assertNotNull(device.freeStorage)

// commented values are not mocked by robolectric
//            assertNotNull(device.batteryLevel)
//            assertNotNull(device.isCharging)
//            assertNotNull(device.batteryTemperature)
//            assertNotNull(device.isOnline)
//            assertNotNull(device.externalFreeStorage)
//            assertNotNull(device.externalStorageSize)
//            assertNotNull(device.connectionType)
        }
    }

    @Test
    fun `Does not collect device info that requires IPC if disabled`() {
        fixture.options.isCollectAdditionalContext = false
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val device = it.contexts.device!!
            assertNull(device.freeMemory)
            assertNull(device.isLowMemory)

// commented values are not mocked by robolectric
//            assertNotNull(device.batteryLevel)
//            assertNotNull(device.isCharging)
//            assertNotNull(device.batteryTemperature)
//            assertNotNull(device.isOnline)
//            assertNotNull(device.connectionType)
        }
    }

    @Test
    fun `Event sets language and locale`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val device = it.contexts.device!!
            assertEquals("en", device.language)
            assertEquals("en_US", device.locale)
        }
    }

    @Test
    fun `Event sets InForeground to true if not in the background`() {
        val sut = fixture.getSut(context)

        AppState.getInstance().setInBackground(false)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val app = it.contexts.app!!
            assertTrue(app.inForeground!!)
        }
    }

    @Test
    fun `Event sets InForeground to false if in the background`() {
        val sut = fixture.getSut(context)

        AppState.getInstance().setInBackground(true)

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val app = it.contexts.app!!
            assertFalse(app.inForeground!!)
        }
    }

    @Test
    fun `Event sets no device cpu info when there is none provided`() {
        val sut = fixture.getSut(context)
        CpuInfoUtils.getInstance().setCpuMaxFrequencies(emptyList())
        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val device = it.contexts.device!!
            assertNull(device.processorCount)
            assertNull(device.processorFrequency)
        }
    }

    @Test
    fun `Event sets rights device cpu info when there is one provided`() {
        val sut = fixture.getSut(context)
        CpuInfoUtils.getInstance().setCpuMaxFrequencies(listOf(800, 900))

        assertNotNull(sut.process(SentryEvent(), Hint())) {
            val device = it.contexts.device!!
            assertEquals(2, device.processorCount)
            assertEquals(900.0, device.processorFrequency)
        }
    }

    @Test
    fun `Events from HybridSDKs don't set main thread and in foreground context`() {
        val sut = fixture.getSut(context)

        val cachedHint = CustomCachedApplyScopeDataHint()
        val hint = HintUtils.createWithTypeCheckHint(cachedHint)

        val sdkVersion = SdkVersion(SENTRY_DART_SDK_NAME, "1.0.0")
        val event = SentryEvent().apply {
            sdk = sdkVersion
            threads = mutableListOf(
                SentryThread().apply {
                    id = 10L
                }
            )
        }
        // set by OutboxSender during event deserialization
        HintUtils.setIsFromHybridSdk(hint, sdkVersion.name)

        assertNotNull(sut.process(event, hint)) {
            val app = it.contexts.app!!
            assertNull(app.inForeground)
            val thread = it.threads!!.first()
            assertNull(thread.isMain)
        }
    }

    @Test
    fun `does not perform root check if root checker is disabled`() {
        fixture.options.isEnableRootCheck = false
        val sut = fixture.getSut(context)

        val contextData = sut.contextData.get()
        assertNull(contextData[ROOTED])
    }
}
