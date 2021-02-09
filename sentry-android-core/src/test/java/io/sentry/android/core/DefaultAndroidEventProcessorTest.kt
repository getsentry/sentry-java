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
import io.sentry.android.core.DefaultAndroidEventProcessor.ANDROID_ID
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
import io.sentry.protocol.User
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

    private class Fixture {
        val buildInfo = mock<IBuildInfoProvider>()
        val options = SentryOptions().apply {
            setDebug(true)
            setLogger(mock())
            sdkVersion = SdkVersion().apply {
                name = "test"
                version = "1.2.3"
            }
        }

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
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, ILogger::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(null, mock<SentryOptions>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null options is provided, invalid argument is thrown`() {
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, ILogger::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(mock<Context>(), null, null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null buildInfo is provided, invalid argument is thrown`() {
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, ILogger::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(null, null, mock<IBuildInfoProvider>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut(context)
        var event = SentryEvent()
        // refactor and mock data later on
        event = sut.process(event, null)
        assertNotNull(event.contexts.app)
        assertEquals("test", event.debugMeta.images[0].uuid)
        assertNotNull(event.dist)
    }

    @Test
    fun `When debug meta is not null, set the image list`() {
        val sut = fixture.getSut(context)
        var event = SentryEvent().apply {
            debugMeta = DebugMeta()
        }

        event = sut.process(event, null)

        assertEquals("test", event.debugMeta.images[0].uuid)
    }

    @Test
    fun `When debug meta is not null and image list is not empty, append to the list`() {
        val sut = fixture.getSut(context)

        val image = DebugImage().apply {
            uuid = "abc"
            type = "proguard"
        }
        var event = SentryEvent().apply {
            debugMeta = DebugMeta().apply {
                images = mutableListOf(image)
            }
        }

        event = sut.process(event, null)

        assertEquals("abc", event.debugMeta.images.first().uuid)
        assertEquals("test", event.debugMeta.images.last().uuid)
    }

    @Test
    fun `Current should be true if it comes from main thread`() {
        val sut = fixture.getSut(context)

        val sentryThread = SentryThread().apply {
            id = Looper.getMainLooper().thread.id
        }
        var event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }
        // refactor and mock data later on
        event = sut.process(event, null)
        assertTrue(event.threads.first().isCurrent)
    }

    @Test
    fun `Current should be false if it its not the main thread`() {
        val sut = fixture.getSut(context)

        val sentryThread = SentryThread().apply {
            id = 10L
        }
        var event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }
        // refactor and mock data later on
        event = sut.process(event, null)
        assertFalse(event.threads.first().isCurrent)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val sut = fixture.getSut(context)

        var event = SentryEvent()
        // refactor and mock data later on
        event = sut.process(event, CachedEvent())
        assertNull(event.contexts.app)
        assertNull(event.debugMeta)
        assertNull(event.release)
        assertNull(event.dist)
    }

    @Test
    fun `When hint is Cached, userId is applied anyway`() {
        val sut = fixture.getSut(context)

        var event = SentryEvent()
        event = sut.process(event, CachedEvent())
        assertNotNull(event.user)
    }

    @Test
    fun `When user with id is already set, do not overwrite it`() {
        val sut = fixture.getSut(context)

        val user = User()
        user.id = "user-id"
        var event = SentryEvent().apply {
            setUser(user)
        }
        event = sut.process(event, null)
        assertNotNull(event.user)
        assertSame(user, event.user)
    }

    @Test
    fun `When user without id is set, user id is applied`() {
        val sut = fixture.getSut(context)

        val user = User()
        var event = SentryEvent().apply {
            setUser(user)
        }
        event = sut.process(event, null)
        assertNotNull(event.user)
        assertNotNull(event.user.id)
    }

    @Test
    fun `Executor service should be called on ctor`() {
        val sut = fixture.getSut(context)

        val contextData = sut.contextData.get()
        assertNotNull(contextData)
        assertEquals("test", (contextData[PROGUARD_UUID] as Array<*>)[0])
        assertNotNull(contextData[ROOTED])
        assertNotNull(contextData[ANDROID_ID])
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

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNotNull(event.getTag("isSideLoaded"))
    }

    @Test
    fun `When event already has OS, add OS with custom key`() {
        val sut = fixture.getSut(context)

        var event = SentryEvent().apply {
            contexts.setOperatingSystem(OperatingSystem().apply {
                name = " Linux "
            })
        }
        event = sut.process(event, null)

        assertEquals(" Linux ", (event.contexts["os_linux"] as OperatingSystem).name)
        assertEquals("Android", event.contexts.operatingSystem!!.name)
    }

    @Test
    fun `When event already has OS, add OS with generated key if no name`() {
        val sut = fixture.getSut(context)

        var event = SentryEvent().apply {
            contexts.setOperatingSystem(OperatingSystem().apply {
                version = "1.0"
            })
        }
        event = sut.process(event, null)

        assertEquals("1.0", (event.contexts["os_1"] as OperatingSystem).version)
    }
}
