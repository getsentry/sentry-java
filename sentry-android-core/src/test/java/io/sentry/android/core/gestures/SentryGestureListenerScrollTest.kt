package io.sentry.android.core.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import android.widget.ListAdapter
import androidx.core.view.ScrollingView
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel.INFO
import io.sentry.android.core.SentryAndroidOptions
import org.junit.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals

class SentryGestureListenerScrollTest {
    class Fixture {
        val window: Window = mock()
        val context: Context = mock()
        val resources: Resources = mock()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val hub: IHub = mock()

        val firstEvent: MotionEvent = mock()
        val eventsInBetween: List<MotionEvent> = listOf(mock(), mock(), mock())
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
                touchWithinBounds = touchWithinBounds
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
            return SentryGestureListener(WeakReference(window), hub, options)
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

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.scroll", it.category)
                assertEquals("user", it.type)
                assertEquals("test_scroll_view", it.data["view.id"])
                assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                assertEquals("left", it.data["direction"])
                assertEquals(INFO, it.level)
            }
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

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
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

        inOrder(fixture.hub) {
            verify(fixture.hub).addBreadcrumb(
                check<Breadcrumb> {
                    assertEquals("ui.swipe", it.category)
                    assertEquals("user", it.type)
                    assertEquals("pager", it.data["view.id"])
                    assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                    assertEquals("down", it.data["direction"])
                    assertEquals(INFO, it.level)
                }
            )
            verify(fixture.hub).addBreadcrumb(
                check<Breadcrumb> {
                    assertEquals("ui.swipe", it.category)
                    assertEquals("user", it.type)
                    assertEquals("pager", it.data["view.id"])
                    assertEquals(fixture.target.javaClass.canonicalName, it.data["view.class"])
                    assertEquals("up", it.data["direction"])
                    assertEquals(INFO, it.level)
                }
            )
        }
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `if no scroll or swipe event occurred, does not capture a breadcrumb`() {
        val sut = fixture.getSut<ScrollableView>()
        sut.onUp(fixture.firstEvent)
        sut.onDown(fixture.endEvent)

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
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
