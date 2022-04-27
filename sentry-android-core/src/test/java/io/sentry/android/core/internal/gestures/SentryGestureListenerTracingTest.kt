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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.android.core.SentryAndroidOptions
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
        lateinit var target: View
        lateinit var transaction: SentryTracer

        internal inline fun <reified T : View> getSut(
            resourceName: String = "test_button",
            hasViewIdInRes: Boolean = true,
            tracesSampleRate: Double? = 1.0,
            isEnableUserInteractionTracing: Boolean = true,
            transaction: SentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
        ): SentryGestureListener {
            options.tracesSampleRate = tracesSampleRate
            options.isEnableUserInteractionTracing = isEnableUserInteractionTracing

            this.transaction = transaction

            target = mockView<T>(event = event, clickable = true)
            window.mockDecorView<ViewGroup>(event = event) {
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

            whenever(hub.startTransaction(any(), any(), any(), anyOrNull(), any<Boolean>()))
                .thenReturn(transaction)
            whenever(hub.options).thenReturn(options)
            return SentryGestureListener(
                activity,
                hub,
                options,
                true
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when tracing is disabled, does not start a transaction`() {
        val sut = fixture.getSut<View>(tracesSampleRate = null)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(), anyOrNull(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `when ui-interaction tracing is disabled, does not start a transaction`() {
        val sut = fixture.getSut<View>(isEnableUserInteractionTracing = false)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(), anyOrNull(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `when view id cannot be retrieved, does not start a transaction`() {
        val sut = fixture.getSut<View>(hasViewIdInRes = false)

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub, never()).startTransaction(
            any(), anyOrNull(), any(), anyOrNull(), any<Boolean>()
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
                assertEquals("Activity.test_button", it)
            },
            anyOrNull(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `captures transaction with interaction event type as op`() {
        val sut = fixture.getSut<View>()

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            any(), check { assertEquals("ui.action.click", it) }, any(),
            anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `starts a new transaction when a new view is interacted with`() {
        // first view interaction
        val sut = fixture.getSut<View>()

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_button", it)
            },
            any(), any(), anyOrNull(), any<Boolean>()
        )

        clearInvocations(fixture.hub)
        // second view interaction with another view
        val newTarget = mockView<View>(event = fixture.event, clickable = true)
        val newContext = mock<Context>()
        val newRes = mock<Resources>()
        newRes.mockForTarget(newTarget, "test_checkbox")
        whenever(newContext.resources).thenReturn(newRes)
        whenever(newTarget.context).thenReturn(newContext)
        fixture.window.mockDecorView<ViewGroup>(event = fixture.event) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(newTarget)
        }

        whenever(fixture.hub.startTransaction(any(), any(), any(), anyOrNull(), any<Boolean>()))
            .thenAnswer {
                // verify that the active transaction gets finished when a new one appears
                assertEquals(true, fixture.transaction.isFinished)
                SentryTracer(TransactionContext("name", "op"), fixture.hub)
            }

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_checkbox", it)
            },
            any(), any(), anyOrNull(), any<Boolean>()
        )
    }

    @Test
    fun `starts a new transaction when the same view was interacted with a different event type`() {
        val sut = fixture.getSut<ScrollableListView>(resourceName = "test_scroll_view")

        sut.onSingleTapUp(fixture.event)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_scroll_view", it)
            },
            check {
                assertEquals("ui.action.click", it)
            },
            any(), anyOrNull(), any<Boolean>()
        )

        clearInvocations(fixture.hub)

        // second view interaction with a different interaction type (scroll)
        whenever(fixture.hub.startTransaction(any(), any(), any(), anyOrNull(), any<Boolean>()))
            .thenAnswer {
                // verify that the active transaction gets finished when a new one appears
                assertEquals(true, fixture.transaction.isFinished)
                SentryTracer(TransactionContext("name", "op"), fixture.hub)
            }

        sut.onScroll(fixture.event, mock(), 10.0f, 0f)
        sut.onUp(mock())

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity.test_scroll_view", it)
            },
            check {
                assertEquals("ui.action.scroll", it)
            },
            any(), anyOrNull(), any<Boolean>()
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
                assertEquals("Activity.test_button", it)
            },
            any(), any(), anyOrNull(), any<Boolean>()
        )

        // second view interaction
        sut.onSingleTapUp(fixture.event)

        verify(fixture.transaction).scheduleFinish(anyOrNull())
    }

    internal open class ScrollableListView : AbsListView(mock()) {
        override fun getAdapter(): ListAdapter = mock()
        override fun setSelection(position: Int) = Unit
    }
}
