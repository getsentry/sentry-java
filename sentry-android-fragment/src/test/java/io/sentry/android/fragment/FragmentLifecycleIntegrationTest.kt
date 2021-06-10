package io.sentry.android.fragment

import android.app.Activity
import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.SentryOptions
import org.junit.Test

class FragmentLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()

        fun getSut(): FragmentLifecycleIntegration {
            return FragmentLifecycleIntegration(application, mock())
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When register, it should register activity lifecycle callbacks`() {
        val sut = fixture.getSut()

        sut.register(mock(), SentryOptions())

        verify(fixture.application).registerActivityLifecycleCallbacks(sut)
    }

    @Test
    fun `When close, it should unregister lifecycle callbacks`() {
        val sut = fixture.getSut()

        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(sut)
    }

    @Test
    fun `When FragmentActivity is created, it should register fragment lifecycle callbacks`() {
        val sut = fixture.getSut()
        val fragmentManager = mock<FragmentManager>()
        val fragmentActivity = mock<FragmentActivity> {
            on { supportFragmentManager } doReturn fragmentManager
        }

        sut.register(mock(), SentryOptions())
        sut.onActivityCreated(fragmentActivity, savedInstanceState = null)

        verify(fragmentManager, times(1)).registerFragmentLifecycleCallbacks(check { fragmentCallbacks ->
            fragmentCallbacks is SentryFragmentLifecycleCallbacks
        }, eq(true))
    }

    @Test
    fun `When not a FragmentActivity is created, it should not crash`() {
        val sut = fixture.getSut()
        val activity = mock<Activity>()

        sut.register(mock(), SentryOptions())
        sut.onActivityCreated(activity, savedInstanceState = null)
    }
}
