package io.sentry.android.fragment

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.ScopeCallback
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.protocol.SentryId
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("SameParameterValue")
class SentryFragmentLifecycleCallbacksTest {
    private class Fixture {
        val fragmentManager = mock<FragmentManager>()
        val scopes = mock<IScopes>()
        val fragment = mock<Fragment>()
        val context = mock<Context>()
        val scope = mock<IScope>()
        val transaction = mock<ITransaction>()
        val span = mock<ISpan>()

        fun getSut(
            loggedFragmentLifecycleStates: Set<FragmentLifecycleState> = FragmentLifecycleState.states,
            enableAutoFragmentLifecycleTracing: Boolean = false,
            tracesSampleRate: Double? = 1.0,
            isAdded: Boolean = true,
        ): SentryFragmentLifecycleCallbacks {
            whenever(scopes.options).thenReturn(
                SentryOptions().apply {
                    setTracesSampleRate(tracesSampleRate)
                },
            )
            whenever(span.spanContext).thenReturn(
                SpanContext(SentryId.EMPTY_ID, SpanId.EMPTY_ID, "op", null, null),
            )
            whenever(transaction.startChild(any<String>(), any<String>())).thenReturn(span)
            whenever(scope.transaction).thenReturn(transaction)
            whenever(scopes.configureScope(any())).thenAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }
            whenever(fragment.isAdded).thenReturn(isAdded)
            return SentryFragmentLifecycleCallbacks(
                scopes = scopes,
                filterFragmentLifecycleBreadcrumbs = loggedFragmentLifecycleStates,
                enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing,
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
    fun `When fragment is attached with subset of logged breadcrumbs, it should add only those breadcrumbs`() {
        val sut = fixture.getSut(loggedFragmentLifecycleStates = setOf(FragmentLifecycleState.CREATED))

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentAttached(fixture.fragmentManager, fixture.fragment, fixture.context)

        verifyBreadcrumbAddedCount(1)
        verifyBreadcrumbAdded("created")
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
            savedInstanceState = null,
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

        verify(fixture.transaction, never()).startChild(any<String>(), any<String>())
    }

    @Test
    fun `When fragment is created, it should start tracing if enabled`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction).startChild(
            check<String> {
                assertEquals(SentryFragmentLifecycleCallbacks.FRAGMENT_LOAD_OP, it)
            },
            check<String> {
                assertEquals("androidx.fragment.app.Fragment", it)
            },
        )
    }

    @Test
    fun `When fragment is created but not added to activity, it should not start tracing`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true, isAdded = false)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction, never()).startChild(any<String>(), any<String>())
    }

    @Test
    fun `When tracing is already running, do not start again`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)

        verify(fixture.transaction).startChild(any<String>(), any<String>())
    }

    @Test
    fun `When fragment is started, it should stop tracing if enabled`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentStarted(fixture.fragmentManager, fixture.fragment)

        verify(fixture.span).finish(
            check {
                assertEquals(SpanStatus.OK, it)
            },
        )
    }

    @Test
    fun `When fragment is started, it should stop tracing if enabled but keep status`() {
        val sut = fixture.getSut(enableAutoFragmentLifecycleTracing = true)

        whenever(fixture.span.status).thenReturn(SpanStatus.ABORTED)
        sut.onFragmentCreated(fixture.fragmentManager, fixture.fragment, savedInstanceState = null)
        sut.onFragmentStarted(fixture.fragmentManager, fixture.fragment)

        verify(fixture.span).finish(
            check {
                assertEquals(SpanStatus.ABORTED, it)
            },
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
            },
        )
    }

    private fun verifyBreadcrumbAdded(expectedState: String) {
        verify(fixture.scopes).addBreadcrumb(
            check { breadcrumb: Breadcrumb ->
                assertEquals("ui.fragment.lifecycle", breadcrumb.category)
                assertEquals("navigation", breadcrumb.type)
                assertEquals(INFO, breadcrumb.level)
                assertEquals(expectedState, breadcrumb.getData("state"))
                assertEquals(fixture.fragment.javaClass.canonicalName, breadcrumb.getData("screen"))
            },
            anyOrNull(),
        )
    }

    private fun verifyBreadcrumbAddedCount(count: Int) {
        verify(fixture.scopes, times(count)).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }
}
