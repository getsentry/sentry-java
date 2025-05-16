package io.sentry.android.core

import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SentryUserFeedbackDialogTest {

    class Fixture {
        private val mockDsn = "http://key@localhost/proj"

        val mockedSentry = mockStatic(Sentry::class.java)
        val mockScopes = mock<IScopes>()
        val mockLogger = mock<ILogger>()
        val options = SentryAndroidOptions().apply {
            dsn = mockDsn
            profilesSampleRate = 1.0
            isDebug = true
            setLogger(mockLogger)
        }

        init {
            whenever(mockScopes.options).thenReturn(options)
            whenever(mockScopes.isEnabled).thenReturn(true)
        }

        fun getSut(): SentryUserFeedbackDialog {
            return SentryUserFeedbackDialog(mock())
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun setUp() {
        fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.mockScopes)
    }

    @AfterTest
    fun cleanup() {
        fixture.mockedSentry.close()
    }

    @Test
    fun `feedback dialog is shown when sdk is enabled`() {
        fixture.options.isEnabled = true
        val sut = fixture.getSut()
        verifyNoInteractions(fixture.mockLogger)
        sut.show()
        verifyNoInteractions(fixture.mockLogger)
    }

    @Test
    fun `feedback dialog is not shown when sdk is disabled`() {
        fixture.options.isEnabled = false
        val sut = fixture.getSut()
        sut.show()
        verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("Sentry is disabled. Feedback dialog won't be shown."))
    }
}
