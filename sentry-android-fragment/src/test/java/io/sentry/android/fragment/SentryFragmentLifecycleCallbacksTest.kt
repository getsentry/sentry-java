package io.sentry.android.fragment

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.Hub
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import org.junit.Test
import kotlin.test.assertEquals

class SentryFragmentLifecycleCallbacksTest {

    private class Fixture {
        val fragmentManager = mock<FragmentManager>()
        val hub = mock<Hub>()
        val fragment = mock<Fragment>()
        val context = mock<Context>()
        val scope = mock<Scope>()
        val transaction = mock<ITransaction>()
        val span = mock<ISpan>()

        fun getSut(
            enableFragmentLifecycleBreadcrumbs: Boolean = true,
            enableAutoFragmentLifecycleTracing: Boolean = false,
            tracesSampleRate: Double? = 1.0
        ): SentryFragmentLifecycleCallbacks {
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    setTracesSampleRate(tracesSampleRate)
                }
            )
            whenever(transaction.startChild(any(), any())).thenReturn(span)
            whenever(scope.transaction).thenReturn(transaction)
            whenever(hub.configureScope(any())).thenAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }
            return SentryFragmentLifecycleCallbacks(
                hub = hub,
                enableFragmentLifecycleBreadcrumbs = enableFragmentLifecycleBreadcrumbs,
                enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing
            )
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
    fun `When fragment is attached with disabled breadcrumbs, it should not add breadcrumb`() {
        val sut = fixture.getSut(enableFragmentLifecycleBreadcrumbs = false)

        sut.onFragmentAttached(fixture.fragmentManager, fixture.fragment, fixture.context)

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
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

    @Test
    fun `When fragment is created, it should not start tracing if disabled`() {
        val sut = fixture.getSut()

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction, never()).startChild(any(), any())
    }

    @Test
    fun `When fragment is created, it should start tracing if enabled`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction).startChild(
            check {
                assertEquals(SentryFragmentLifecycleCallbacks.FRAGMENT_LOAD_OP, it)
            },
            check {
                assertEquals("Fragment", it)
            }
        )
    }

    @Test
    fun `When tracing is already running, do not start again`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction).startChild(any(), any())
    }

    @Test
    fun `When fragment is resumed, it should stop tracing if enabled`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentResumed(fixture.fragmentManager, fixture.fragment)

        verify(fixture.span).finish(
            check {
                assertEquals(SpanStatus.OK, it)
            }
        )
    }

    @Test
    fun `When fragment is resumed, it should stop tracing if enabled but keep status`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        whenever(fixture.span.status).thenReturn(SpanStatus.ABORTED)
        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentResumed(fixture.fragmentManager, fixture.fragment)

        verify(fixture.span).finish(
            check {
                assertEquals(SpanStatus.ABORTED, it)
            }
        )
    }

    @Test
    fun `When fragment is destroyed, it should stop tracing if enabled`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentDestroyed(fixture.fragmentManager, fixture.fragment)

        verify(fixture.span).finish(
            check {
                assertEquals(SpanStatus.OK, it)
            }
        )
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
