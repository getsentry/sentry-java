package io.sentry.android.core

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.DiagnosticLogger
import io.sentry.ILogger
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.android.core.DefaultAndroidEventProcessor.EMULATOR
import io.sentry.android.core.DefaultAndroidEventProcessor.KERNEL_VERSION
import io.sentry.android.core.DefaultAndroidEventProcessor.PROGUARD_UUID
import io.sentry.android.core.DefaultAndroidEventProcessor.ROOTED
import io.sentry.android.core.DefaultAndroidEventProcessor.SIDE_LOADED
import io.sentry.protocol.DebugImage
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryThread
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.getCtor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultAndroidEventProcessorTest {
    private lateinit var context: Context

    private val className = "io.sentry.android.core.DefaultAndroidEventProcessor"
    private val ctorTypes = arrayOf(Context::class.java, ILogger::class.java, IBuildInfoProvider::class.java)

    private class Fixture {
        val buildInfo = mock<IBuildInfoProvider>()
        val options = SentryOptions().apply {
            setDebug(true)
            setLogger(mock())
            sdkVersion = SdkVersion("test", "1.2.3")
        }
        val sentryTracer = SentryTracer(TransactionContext("", ""), mock())

        fun getSut(context: Context): DefaultAndroidEventProcessor {
            return DefaultAndroidEventProcessor(context, options.logger, buildInfo)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when instance is created, application context reference is stored`() {
        val sut = fixture.getSut(context)

        assertEquals(sut.context, context)
    }

    @Test
    fun `when null context is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(null, mock<SentryOptions>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null options is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(mock<Context>(), null, null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null buildInfo is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        val params = arrayOf(null, null, mock<IBuildInfoProvider>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `When Event and hint is not Cached, data should be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), null)) {
            assertNotNull(it.contexts.app)
            assertEquals("test", it.debugMeta.images[0].uuid)
            assertNotNull(it.dist)
        }
    }

    @Test
    fun `When Transaction and hint is not Cached, data should be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), null)) {
            assertNotNull(it.contexts.app)
            assertNotNull(it.dist)
        }
    }

    @Test
    fun `When debug meta is not null, set the image list`() {
        val sut = fixture.getSut(context)
        val event = SentryEvent().apply {
            debugMeta = DebugMeta()
        }

        assertNotNull(sut.process(event, null)) {
            assertEquals("test", it.debugMeta.images[0].uuid)
        }
    }

    @Test
    fun `When debug meta is not null and image list is not empty, append to the list`() {
        val sut = fixture.getSut(context)

        val image = DebugImage().apply {
            uuid = "abc"
            type = "proguard"
        }
        val event = SentryEvent().apply {
            debugMeta = DebugMeta().apply {
                images = mutableListOf(image)
            }
        }

        assertNotNull(sut.process(event, null)) {
            assertEquals("abc", it.debugMeta.images.first().uuid)
            assertEquals("test", it.debugMeta.images.last().uuid)
        }
    }

    @Test
    fun `Current should be true if it comes from main thread`() {
        val sut = fixture.getSut(context)

        val sentryThread = SentryThread().apply {
            id = Looper.getMainLooper().thread.id
        }
        val event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }

        assertNotNull(sut.process(event, null)) {
            assertTrue(it.threads.first().isCurrent)
        }
    }

    @Test
    fun `Current should be false if it its not the main thread`() {
        val sut = fixture.getSut(context)

        val event = SentryEvent().apply {
            threads = mutableListOf(SentryThread().apply {
                id = 10L
            })
        }

        assertNotNull(sut.process(event, null)) {
            assertFalse(it.threads.first().isCurrent)
        }
    }

    @Test
    fun `When Event and hint is Cached, data should not be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), CachedEvent())) {
            assertNull(it.contexts.app)
            assertNull(it.debugMeta)
            assertNull(it.dist)
        }
    }

    @Test
    fun `When Transaction and hint is Cached, data should not be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), CachedEvent())) {
            assertNull(it.contexts.app)
            assertNull(it.dist)
        }
    }

    @Test
    fun `When Event and hint is Cached, userId is applied anyway`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), CachedEvent())) {
            assertNotNull(it.user)
        }
    }

    @Test
    fun `When Transaction and hint is Cached, userId is applied anyway`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), CachedEvent())) {
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

        assertNotNull(sut.process(event, null)) {
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

        assertNotNull(sut.process(event, null)) {
            assertNotNull(it.user)
            assertNotNull(it.user!!.id)
        }
    }

    @Test
    fun `Executor service should be called on ctor`() {
        val sut = fixture.getSut(context)

        val contextData = sut.contextData.get()

        assertNotNull(contextData)
        assertEquals("test", (contextData[PROGUARD_UUID] as Array<*>)[0])
        assertNotNull(contextData[ROOTED])
        assertNotNull(contextData[KERNEL_VERSION])
        assertNotNull(contextData[EMULATOR])
        assertNotNull(contextData[SIDE_LOADED])
    }

    @Test
    fun `Processor won't throw exception`() {
        val sut = fixture.getSut(context)

        sut.process(SentryEvent(), null)

        verify((fixture.options.logger as DiagnosticLogger).logger, never())!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `Processor won't throw exception when theres a hint`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options.logger, fixture.buildInfo, mock())

        processor.process(SentryEvent(), CachedEvent())

        verify((fixture.options.logger as DiagnosticLogger).logger, never())!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `When event is processed, sideLoaded info should be set`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), null)) {
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

        assertNotNull(sut.process(event, null)) {
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

        assertNotNull(sut.process(event, null)) {
            assertSame(osNoName, (it.contexts["os_1"] as OperatingSystem))
            assertEquals("Android", it.contexts.operatingSystem!!.name)
        }
    }

    @Test
    fun `When hint is Cached, memory data should not be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), CachedEvent())) {
            assertNull(it.contexts.device!!.freeMemory)
            assertNull(it.contexts.device!!.isLowMemory)
        }
    }

    @Test
    fun `When hint is not Cached, memory data should be applied`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryEvent(), null)) {
            assertNotNull(it.contexts.device!!.freeMemory)
            assertNotNull(it.contexts.device!!.isLowMemory)
        }
    }

    @Test
    fun `Device's context is set on transactions`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), null)) {
            assertNotNull(it.contexts.device)
        }
    }

    @Test
    fun `Device's OS is set on transactions`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), null)) {
            assertNotNull(it.contexts.operatingSystem)
        }
    }

    @Test
    fun `Transaction do not set device's context that requires heavy work`() {
        val sut = fixture.getSut(context)

        assertNotNull(sut.process(SentryTransaction(fixture.sentryTracer), null)) {
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

        assertNotNull(sut.process(SentryEvent(), null)) {
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
}
