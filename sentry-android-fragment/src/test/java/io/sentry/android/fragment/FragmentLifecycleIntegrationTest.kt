package io.sentry.android.fragment

import android.app.Activity
import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.SentryOptions
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FragmentLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val fragmentManager = mock<FragmentManager>()
        val fragmentActivity = mock<FragmentActivity> {
            on { supportFragmentManager } doReturn fragmentManager
        }
        val hub = mock<IHub>()
        val options = SentryOptions()

        fun getSut(
            enableFragmentLifecycleBreadcrumbs: Boolean = true,
            enableAutoFragmentLifecycleTracing: Boolean = false
        ): FragmentLifecycleIntegration {
            whenever(hub.options).thenReturn(options)
            return FragmentLifecycleIntegration(
                application = application,
                enableFragmentLifecycleBreadcrumbs = enableFragmentLifecycleBreadcrumbs,
                enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When register, it should register activity lifecycle callbacks`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(sut)
    }

    @Test
    fun `When close, it should unregister lifecycle callbacks`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
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

        sut.register(fixture.hub, fixture.options)
        sut.onActivityCreated(fragmentActivity, savedInstanceState = null)

        verify(fragmentManager).registerFragmentLifecycleCallbacks(
            check { fragmentCallbacks ->
                fragmentCallbacks is SentryFragmentLifecycleCallbacks
            },
            eq(true)
        )
    }

    @Test
    fun `When FragmentActivity is created, it should register fragment lifecycle callbacks with passed config`() {
        val sut = fixture.getSut(enableFragmentLifecycleBreadcrumbs = false, enableAutoFragmentLifecycleTracing = true)

        sut.register(fixture.hub, fixture.options)
        sut.onActivityCreated(fixture.fragmentActivity, savedInstanceState = null)

        verify(fixture.fragmentManager).registerFragmentLifecycleCallbacks(
            check { fragmentCallbacks ->
                val callback = (fragmentCallbacks as SentryFragmentLifecycleCallbacks)
                assertTrue(callback.enableAutoFragmentLifecycleTracing)
                assertFalse(callback.enableFragmentLifecycleBreadcrumbs)
            },
            eq(true)
        )
    }

    @Test
    fun `When not a FragmentActivity is created, it should not crash`() {
        val sut = fixture.getSut()
        val activity = mock<Activity>()

        sut.register(fixture.hub, fixture.options)
        sut.onActivityCreated(activity, savedInstanceState = null)
    }

    @Test
    fun `When close is called without register, it should not crash`() {
        val sut = fixture.getSut()

        sut.close()
    }
}
