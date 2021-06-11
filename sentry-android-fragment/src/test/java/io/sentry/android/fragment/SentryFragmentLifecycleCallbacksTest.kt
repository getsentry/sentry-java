package io.sentry.android.fragment

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.Breadcrumb
import io.sentry.Hub
import io.sentry.SentryLevel.INFO
import kotlin.test.assertEquals
import org.junit.Test

class SentryFragmentLifecycleCallbacksTest {

    private class Fixture {
        val fragmentManager = mock<FragmentManager>()
        val hub = mock<Hub>()
        val fragment = mock<Fragment>()
        val context = mock<Context>()

        fun getSut(): SentryFragmentLifecycleCallbacks {
            return SentryFragmentLifecycleCallbacks(hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When fragment is attached, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentAttached(fixture.fragmentManager, fixture.fragment, fixture.context)

        verifyBreadcrumbAdded("attached")
    }

    @Test
    fun `When fragment saved instance state, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentSaveInstanceState(fixture.fragmentManager, fixture.fragment, Bundle())

        verifyBreadcrumbAdded("save instance state")
    }

    @Test
    fun `When fragment is created, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verifyBreadcrumbAdded("created")
    }

    @Test
    fun `When fragments view is created, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentViewCreated(
            fixture.fragmentManager,
            fixture.fragment,
            view = mock(),
            savedInstanceState = null
        )

        verifyBreadcrumbAdded("view created")
    }

    @Test
    fun `When fragment is started, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentStarted(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("started")
    }

    @Test
    fun `When fragment is resumed, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentResumed(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("resumed")
    }

    @Test
    fun `When fragment is paused, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentPaused(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("paused")
    }

    @Test
    fun `When fragment is stopped, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentStopped(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("stopped")
    }

    @Test
    fun `When fragments view is destroyed, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentViewDestroyed(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("view destroyed")
    }

    @Test
    fun `When fragment is destroyed, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentDestroyed(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("destroyed")
    }

    @Test
    fun `When fragment is detached, it should add breadcrumb`() {
        val sut = fixture.getSut()

        sut.onFragmentDetached(fixture.fragmentManager, fixture.fragment)

        verifyBreadcrumbAdded("detached")
    }

    private fun verifyBreadcrumbAdded(expectedState: String) {
        verify(fixture.hub).addBreadcrumb(
            check { breadcrumb: Breadcrumb ->
                assertEquals("ui.fragment.lifecycle", breadcrumb.category)
                assertEquals("navigation", breadcrumb.type)
                assertEquals(INFO, breadcrumb.level)
                assertEquals(expectedState, breadcrumb.getData("state"))
                assertEquals(fixture.fragment.javaClass.simpleName, breadcrumb.getData("screen"))
            }
        )
    }
}
