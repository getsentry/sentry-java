package io.sentry.android.core

import android.app.Activity
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CurrentActivityIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val activity = mock<Activity>()
        val scopes = mock<IScopes>()

        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }

        fun getSut(): CurrentActivityIntegration {
            val integration = CurrentActivityIntegration(application)
            integration.register(scopes, options)
            return integration
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        fixture = Fixture()
    }

    @Test
    fun `when the integration is added registerActivityLifecycleCallbacks is called`() {
        fixture.getSut()
        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when the integration is closed unregisterActivityLifecycleCallbacks is called`() {
        val sut = fixture.getSut()
        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when an activity is created the activity holder provides it`() {
        val sut = fixture.getSut()

        sut.onActivityCreated(fixture.activity, null)
        assertEquals(fixture.activity, CurrentActivityHolder.getInstance().activity)
    }

    @Test
    fun `when there is no active activity the holder does not provide an outdated one`() {
        val sut = fixture.getSut()

        sut.onActivityCreated(fixture.activity, null)
        sut.onActivityDestroyed(fixture.activity)

        assertNull(CurrentActivityHolder.getInstance().activity)
    }

    @Test
    fun `when a second activity is started it gets the current one`() {
        val sut = fixture.getSut()

        sut.onActivityCreated(fixture.activity, null)
        sut.onActivityStarted(fixture.activity)
        sut.onActivityResumed(fixture.activity)

        val secondActivity = mock<Activity>()
        sut.onActivityCreated(secondActivity, null)
        sut.onActivityStarted(secondActivity)

        assertEquals(secondActivity, CurrentActivityHolder.getInstance().activity)
    }

    @Test
    fun `destroying an old activity keeps the current one`() {
        val sut = fixture.getSut()

        sut.onActivityCreated(fixture.activity, null)
        sut.onActivityStarted(fixture.activity)
        sut.onActivityResumed(fixture.activity)

        val secondActivity = mock<Activity>()
        sut.onActivityCreated(secondActivity, null)
        sut.onActivityStarted(secondActivity)

        sut.onActivityPaused(fixture.activity)
        sut.onActivityStopped(fixture.activity)
        sut.onActivityDestroyed(fixture.activity)

        assertEquals(secondActivity, CurrentActivityHolder.getInstance().activity)
    }
}
