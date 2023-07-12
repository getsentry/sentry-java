package io.sentry.android.core.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import android.widget.ListAdapter
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.TransactionNameSource
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryGestureListenerTracingTest {
    class Fixture {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val context = mock<Context>()
        val resources = mock<Resources>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val hub = mock<IHub>()
        val event = mock<MotionEvent>()
        val scope = mock<Scope>()
        lateinit var target: View
        lateinit var transaction: SentryTracer

        internal inline fun <reified T : View> getSut(
            resourceName: String = "test_button",
            hasViewIdInRes: Boolean = true,
            tracesSampleRate: Double? = 1.0,
            isEnableUserInteractionTracing: Boolean = true,
            transaction: SentryTracer? = null
        ): SentryGestureListener {
            options.tracesSampleRate = tracesSampleRate
            options.isEnableUserInteractionTracing = isEnableUserInteractionTracing
            options.isEnableUserInteractionBreadcrumbs = true
            options.gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(true))

            whenever(hub.options).thenReturn(options)

            this.transaction = transaction ?: SentryTracer(TransactionContext("name", "op"), hub)

            target = mockView<T>(event = event, clickable = true, context = context)
            window.mockDecorView<ViewGroup>(event = event, context = context) {
                whenever(it.childCount).thenReturn(1)
                whenever(it.getChildAt(0)).thenReturn(target)
            }

            if (hasViewIdInRes) {
                resources.mockForTarget(target, resourceName)
            } else {
                whenever(resources.getResourceEntryName(target.id)).thenThrow(
                    Resources.NotFoundException()
                )
            }
            whenever(context.resources).thenReturn(resources)
            whenever(target.context).thenReturn(context)

            whenever(activity.window).thenReturn(window)

            whenever(hub.startTransaction(any(), any<TransactionOptions>()))
                .thenReturn(this.transaction)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(hub).configureScope(any())

            return SentryGestureListener(
                activity,
                hub,
                options
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when tracing is disabled, does not start a transaction`() {
        val sut = fixture.getSut<View>(tracesSampleRate = null)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any<TransactionOptions>()
        )
    }

    @Test
    fun `when ui-interaction tracing is disabled, does not start a transaction`() {
        val sut = fixture.getSut<View>(isEnableUserInteractionTracing = false)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any<TransactionOptions>()
        )
    }

    @Test
    fun `when view id cannot be retrieved, does not start a transaction`() {
        val sut = fixture.getSut<View>(hasViewIdInRes = false)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(),
            any<TransactionOptions>()
        )
    }

    @Test
    fun `when transaction is created, set transaction to the bound Scope`() {
        val sut = fixture.getSut<View>()

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)

            sut.applyScope(scope, fixture.transaction)

            assertNotNull(scope.transaction)
        }

        sut.onSingleTapUp(fixture.event)
    }

    @Test
    fun `when transaction is created, do not overwrite transaction already bound to the Scope`() {
        val sut = fixture.getSut<View>()

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)
            val previousTransaction = SentryTracer(TransactionContext("name", "op"), fixture.hub)
            scope.transaction = previousTransaction

            sut.applyScope(scope, fixture.transaction)

            assertEquals(previousTransaction, scope.transaction)
        }

        sut.onSingleTapUp(fixture.event)
    }

    @Test
    fun `stopTracing remove transaction from scope`() {
        val sut = fixture.getSut<View>()
        val expectedStatus = SpanStatus.CANCELLED

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)

            sut.applyScope(scope, fixture.transaction)
        }
        sut.onSingleTapUp(fixture.event)

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)

            scope.transaction = fixture.transaction

            sut.clearScope(scope)

            assertEquals(expectedStatus, fixture.transaction.status)
            assertNull(scope.transaction)
        }
        sut.stopTracing(expectedStatus)
    }

    @Test
    fun `captures transaction with activity name + view id as transaction name`() {
        val sut = fixture.getSut<View>()

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_button", it.name)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `captures transaction with interaction event type as op`() {
        val sut = fixture.getSut<View>()

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("ui.action.click", it.operation)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `starts a new transaction when a new view is interacted with`() {
        // first view interaction
        val sut = fixture.getSut<View>()

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_button", it.name)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )

        clearInvocations(fixture.hub)
        // second view interaction with another view
        val newTarget = mockView<View>(event = fixture.event, clickable = true, context = fixture.context)
        val newContext = mock<Context>()
        val newRes = mock<Resources>()
        newRes.mockForTarget(newTarget, "test_checkbox")
        whenever(newContext.resources).thenReturn(newRes)
        whenever(newTarget.context).thenReturn(newContext)
        fixture.window.mockDecorView<ViewGroup>(event = fixture.event, context = fixture.context) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(newTarget)
        }

        whenever(fixture.hub.startTransaction(any(), any<TransactionOptions>()))
            .thenAnswer {
                // verify that the active transaction gets finished when a new one appears
                assertEquals(true, fixture.transaction.isFinished)
                SentryTracer(TransactionContext("name", "op"), fixture.hub)
            }

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_checkbox", it.name)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `starts a new transaction when the same view was interacted with a different event type`() {
        val sut = fixture.getSut<ScrollableListView>(resourceName = "test_scroll_view")

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_scroll_view", it.name)
                assertEquals("ui.action.click", it.operation)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )

        clearInvocations(fixture.hub)

        // second view interaction with a different interaction type (scroll)
        whenever(fixture.hub.startTransaction(any(), any<TransactionOptions>()))
            .thenAnswer {
                // verify that the active transaction gets finished when a new one appears
                assertEquals(true, fixture.transaction.isFinished)
                SentryTracer(TransactionContext("name", "op"), fixture.hub)
            }

        sut.onScroll(fixture.event, mock(), 10.0f, 0f)
        sut.onUp(mock())

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_scroll_view", it.name)
                assertEquals("ui.action.scroll", it.operation)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `resets the idleTimeout when the same view was clicked and the transaction was still active`() {
        // first view interaction
        val transaction = mock<SentryTracer>()
        val sut = fixture.getSut<View>(transaction = transaction)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_button", it.name)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )

        // second view interaction
        sut.onSingleTapUp(fixture.event)

        verify(fixture.transaction).scheduleFinish()
    }

    internal open class ScrollableListView : AbsListView(mock()) {
        override fun getAdapter(): ListAdapter = mock()
        override fun setSelection(position: Int) = Unit
    }
}
