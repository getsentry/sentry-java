package io.sentry.android.core

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ReplayController
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class SentryUserFeedbackDialogTest {
    class Fixture {
        val application: Context = ApplicationProvider.getApplicationContext()
        private val mockDsn = "http://key@localhost/proj"

        val mockedSentry = mockStatic(Sentry::class.java)
        val mockScope = mock<IScope>()
        val mockScopes = mock<IScopes>()
        val mockLogger = mock<ILogger>()
        val mockReplayController = mock<ReplayController>()

        val options =
            SentryAndroidOptions().apply {
                dsn = mockDsn
                profilesSampleRate = 1.0
                isDebug = true
                setLogger(mockLogger)
                setReplayController(mockReplayController)
            }

        init {
            whenever(mockScope.user).thenReturn(mock())
            whenever(mockScopes.scope).thenReturn(mockScope)
            whenever(mockScopes.options).thenReturn(options)
            whenever(mockScopes.isEnabled).thenReturn(true)
        }

        fun getSut(configuration: SentryUserFeedbackDialog.OptionsConfiguration? = null): SentryUserFeedbackDialog =
            SentryUserFeedbackDialog(application, 0, configuration)
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

    @Test
    fun `when feedback dialog is shown, replay is captured`() {
        fixture.options.isEnabled = true
        val sut = fixture.getSut()
        verifyNoInteractions(fixture.mockReplayController)
        sut.show()
        verify(fixture.mockReplayController).captureReplay(eq(false))
    }

    @Test
    fun `when configuration is passed, it is applied to the current dialog only`() {
        fixture.options.isEnabled = true
        val sut =
            fixture.getSut { context, options ->
                options.formTitle = "custom title"
            }
        assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
        sut.show()
        // After showing the dialog, the title should be set
        assertEquals("custom title", sut.findViewById<TextView>(R.id.sentry_dialog_user_feedback_title).text)
        // And the original options should not be modified
        assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
    }
}
