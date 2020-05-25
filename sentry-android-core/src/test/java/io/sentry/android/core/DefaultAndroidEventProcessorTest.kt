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
import io.sentry.android.core.DefaultAndroidEventProcessor.ANDROID_ID
import io.sentry.android.core.DefaultAndroidEventProcessor.EMULATOR
import io.sentry.android.core.DefaultAndroidEventProcessor.KERNEL_VERSION
import io.sentry.android.core.DefaultAndroidEventProcessor.PROGUARD_UUID
import io.sentry.android.core.DefaultAndroidEventProcessor.ROOTED
import io.sentry.core.DiagnosticLogger
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.protocol.SentryThread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultAndroidEventProcessorTest {
    private lateinit var context: Context

    private class Fixture {
        val buildInfo = mock<IBuildInfoProvider>()
        val options = SentryOptions().apply {
            isDebug = true
            setLogger(mock())
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when instance is created, application context reference is stored`() {
        val sut = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)

        assertEquals(sut.context, context)
    }

    @Test
    fun `when null context is provided, invalid argument is thrown`() {
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, SentryOptions::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(null, mock<SentryOptions>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null options is provided, invalid argument is thrown`() {
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, SentryOptions::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(mock<Context>(), null, null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when null buildInfo is provided, invalid argument is thrown`() {
        val clazz = Class.forName("io.sentry.android.core.DefaultAndroidEventProcessor")
        val ctor = clazz.getConstructor(Context::class.java, SentryOptions::class.java, IBuildInfoProvider::class.java)
        val params = arrayOf(null, null, mock<IBuildInfoProvider>())
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)
        var event = SentryEvent().apply {
        }
        // refactor and mock data later on
        event = processor.process(event, null)
        assertNotNull(event.user)
        assertNotNull(event.contexts.app)
        assertEquals("test", event.debugMeta.images[0].uuid)
        assertNotNull(event.sdk)
        assertNotNull(event.dist)
    }

    @Test
    fun `Current should be true if it comes from main thread`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)
        val sentryThread = SentryThread().apply {
            id = Looper.getMainLooper().thread.id
        }
        var event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }
        // refactor and mock data later on
        event = processor.process(event, null)
        assertTrue(event.threads.first().isCurrent)
    }

    @Test
    fun `Current should be false if it its not the main thread`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)
        val sentryThread = SentryThread().apply {
            id = 10L
        }
        var event = SentryEvent().apply {
            threads = mutableListOf(sentryThread)
        }
        // refactor and mock data later on
        event = processor.process(event, null)
        assertFalse(event.threads.first().isCurrent)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)
        var event = SentryEvent().apply {
        }
        // refactor and mock data later on
        event = processor.process(event, CachedEvent())
        assertNull(event.user)
        assertNull(event.contexts.app)
        assertNull(event.debugMeta)
        assertNull(event.sdk)
        assertNull(event.release)
        assertNull(event.dist)
    }

    @Test
    fun `Executor service should be called on ctor`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo)
        val contextData = processor.contextData.get()
        assertNotNull(contextData)
        assertEquals("test", (contextData[PROGUARD_UUID] as Array<*>)[0])
        assertNotNull(contextData[ROOTED])
        assertNotNull(contextData[ANDROID_ID])
        assertNotNull(contextData[KERNEL_VERSION])
        assertNotNull(contextData[EMULATOR])
    }

    @Test
    fun `Processor won't throw exception`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo, mock())
        processor.process(SentryEvent(), null)
        verify((fixture.options.logger as DiagnosticLogger).logger, never())!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `Processor won't throw exception when theres a hint`() {
        val processor = DefaultAndroidEventProcessor(context, fixture.options, fixture.buildInfo, mock())
        processor.process(SentryEvent(), CachedEvent())
        verify((fixture.options.logger as DiagnosticLogger).logger, never())!!.log(eq(SentryLevel.ERROR), any<String>(), any())
    }
}
