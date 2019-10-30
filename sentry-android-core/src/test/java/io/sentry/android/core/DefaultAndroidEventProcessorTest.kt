package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.DiagnosticLogger
import io.sentry.core.ILogger
import io.sentry.core.SentryOptions
import java.lang.IllegalArgumentException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultAndroidEventProcessorTest {
    private lateinit var context: Context

    private class Fixture {
        var options: SentryOptions?
        var logger: ILogger? = mock()

        init {
            val options = SentryOptions()
            options.isDebug = true
            options.setLogger(logger)
            this.options = options
        }

        fun getSut(): DiagnosticLogger {
            return DiagnosticLogger(options, logger)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when instance is created, application context reference is stored`() {
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }
        val sut = DefaultAndroidEventProcessor(mockContext, fixture.options)

        assertEquals(sut.context, context)
    }

    @Test
    fun `when null context is provided, invalid argument is thrown`() {
        assertFailsWith<IllegalArgumentException> { DefaultAndroidEventProcessor(null, fixture.options) }
    }

    @Test
    fun `when null options is provided, invalid argument is thrown`() {
        assertFailsWith<IllegalArgumentException> { DefaultAndroidEventProcessor(context, null) }
    }
}
