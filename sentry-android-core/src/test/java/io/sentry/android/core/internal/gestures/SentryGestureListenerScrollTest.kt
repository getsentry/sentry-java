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
import androidx.core.view.ScrollingView
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryLevel.INFO
import io.sentry.android.core.SentryAndroidOptions
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryGestureListenerScrollTest {
    class Fixture {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val context = mock<Context>()
        val resources = mock<Resources>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
            isEnableUserInteractionBreadcrumbs = true
            isEnableUserInteractionTracing = true
            gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(true))
        }
        val scopes = mock<IScopes>()
        val scope = mock<IScope>()
        val propagationContext = PropagationContext()

        val firstEvent = mock<MotionEvent>()
        val eventsInBetween = listOf(mock<MotionEvent>(), mock(), mock())
        val endEvent = eventsInBetween.last()
        lateinit var target: View
        val directions = setOf("up", "down", "left", "right")

        internal inline fun <reified T : View> getSut(
            resourceName: String = "test_scroll_view",
            touchWithinBounds: Boolean = true,
            direction: String = ""
        ): SentryGestureListener {
            target = mockView<T>(
                event = firstEvent,
                touchWithinBounds = touchWithinBounds,
                context = context
            )
            window.mockDecorView<ViewGroup>(event = firstEvent) {
                whenever(it.childCount).thenReturn(1)
                whenever(it.getChildAt(0)).thenReturn(target)
            }

            resources.mockForTarget(target, resourceName)
            whenever(context.resources).thenReturn(resources)
            whenever(target.context).thenReturn(context)

            if (direction in directions) {
                endEvent.mockDirection(firstEvent, direction)
            }
            whenever(activity.window).thenReturn(window)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(any())
            doAnswer { (it.arguments[0] as Scope.IWithPropagationContext).accept(propagationContext); propagationContext }.whenever(scope).withPropagationContext(any())
            return SentryGestureListener(
                activity,
                scopes,
                options
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `captures a scroll breadcrumb`() {
        val sut = fixture.getSut<ScrollableListView>(direction = "left")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.scroll", it.category)
                assertEquals("user", it.type)
                assertEquals("test_scroll_view", it.data["view.id"])
                assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                assertEquals("left", it.data["direction"])
                assertEquals(INFO, it.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `if no target found, does not capture a breadcrumb`() {
        val sut = fixture.getSut<ScrollableListView>(touchWithinBounds = false)

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `resets scroll state between gestures`() {
        val sut = fixture.getSut<ScrollableView>(resourceName = "pager", direction = "down")

        // first scroll down
        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach { sut.onScroll(fixture.firstEvent, it, 0f, 30.0f) }
        sut.onFling(fixture.firstEvent, fixture.endEvent, 1.0f, 1.0f)
        sut.onUp(fixture.endEvent)

        // second scroll up
        fixture.endEvent.mockDirection(fixture.firstEvent, "up")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach { sut.onScroll(fixture.firstEvent, it, 0f, -30.0f) }
        sut.onFling(fixture.firstEvent, fixture.endEvent, 1.0f, 1.0f)
        sut.onUp(fixture.endEvent)

        inOrder(fixture.scopes) {
            verify(fixture.scopes).addBreadcrumb(
                check<Breadcrumb> {
                    assertEquals("ui.swipe", it.category)
                    assertEquals("user", it.type)
                    assertEquals("pager", it.data["view.id"])
                    assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                    assertEquals("down", it.data["direction"])
                    assertEquals(INFO, it.level)
                },
                anyOrNull()
            )
            verify(fixture.scopes).configureScope(anyOrNull())
            verify(fixture.scopes).addBreadcrumb(
                check<Breadcrumb> {
                    assertEquals("ui.swipe", it.category)
                    assertEquals("user", it.type)
                    assertEquals("pager", it.data["view.id"])
                    assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                    assertEquals("up", it.data["direction"])
                    assertEquals(INFO, it.level)
                },
                anyOrNull()
            )
        }
        verifyNoMoreInteractions(fixture.scopes)
    }

    @Test
    fun `if no scroll or swipe event occurred, does not capture a breadcrumb`() {
        val sut = fixture.getSut<ScrollableView>()
        sut.onUp(fixture.firstEvent)
        sut.onDown(fixture.endEvent)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `if androidX is not available, does not capture a breadcrumb for ScrollingView`() {
        val sut = fixture.getSut<ScrollableView>()

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `starts a new trace on scroll`() {
        val sut = fixture.getSut<ScrollableListView>(direction = "left")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(fixture.scope).propagationContext = any()
    }

    @Test
    fun `does not start a new trace on repeated scroll but does for a different event`() {
        val sut = fixture.getSut<ScrollableListView>(direction = "left")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(fixture.scope).propagationContext = any()

        clearInvocations(fixture.scope)

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)
        verify(fixture.scope, never()).propagationContext = any()

        clearInvocations(fixture.scope)

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach { sut.onScroll(fixture.firstEvent, it, 0f, 30.0f) }
        sut.onFling(fixture.firstEvent, fixture.endEvent, 1.0f, 1.0f)
        sut.onUp(fixture.endEvent)

        verify(fixture.scope).propagationContext = any()
    }

    internal class ScrollableView : View(mock()), ScrollingView {
        override fun computeVerticalScrollOffset(): Int = 0
        override fun computeVerticalScrollExtent(): Int = 0
        override fun computeVerticalScrollRange(): Int = 0
        override fun computeHorizontalScrollOffset(): Int = 0
        override fun computeHorizontalScrollRange(): Int = 0
        override fun computeHorizontalScrollExtent(): Int = 0
    }

    internal open class ScrollableListView : AbsListView(mock()) {
        override fun getAdapter(): ListAdapter = mock()
        override fun setSelection(position: Int) = Unit
    }

    companion object {

        private fun MotionEvent.mockDirection(
            firstEvent: MotionEvent,
            direction: String
        ) {
            val initialStartX = firstEvent.x
            val initialStartY = firstEvent.y
            when (direction) {
                "up" -> {
                    whenever(x).thenReturn(initialStartX)
                    whenever(y).thenReturn((initialStartY - 2))
                }
                "down" -> {
                    whenever(x).thenReturn(initialStartX)
                    whenever(y).thenReturn((initialStartY + 2))
                }
                "right" -> {
                    whenever(x).thenReturn((initialStartX + 2))
                    whenever(y).thenReturn(initialStartY)
                }
                "left" -> {
                    whenever(x).thenReturn((initialStartX - 2))
                    whenever(y).thenReturn(initialStartY)
                }
            }
        }
    }
}
