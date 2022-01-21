package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.RadioButton
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel.INFO
import io.sentry.android.core.SentryAndroidOptions
import org.junit.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals

class SentryGestureListenerClickTest {
    class Fixture {
        val window = mock<Window>()
        val context = mock<Context>()
        val resources = mock<Resources>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val hub = mock<IHub>()

        fun getSut(
            target: View,
            resourceName: String = "test_button",
        ): SentryGestureListener {
            resources.mockForTarget(target, resourceName)
            whenever(context.resources).thenReturn(resources)
            whenever(target.context).thenReturn(context)
            return SentryGestureListener(
                WeakReference(window),
                hub,
                options,
                true
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when target and its ViewGroup are clickable, captures a breadcrumb for target`() {
        val event = mock<MotionEvent>()
        val container1 = mockView<ViewGroup>(
            id = View.generateViewId(),
            event = event,
            touchWithinBounds = false,
        )
        val target = mockView<View>(
            id = View.generateViewId(),
            event = event,
            clickable = true
        )
        val notClickableInvalidTarget = mockView<View>(
            id = View.generateViewId(),
            event = event,
        )
        val notVisibleInvalidTarget = mockView<View>(
            id = View.generateViewId(),
            event = event,
            visible = false,
        )
        val container2 = mockView<ViewGroup>(
            id = View.generateViewId(),
            event = event,
            clickable = true
        ) {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(notClickableInvalidTarget)
            whenever(it.getChildAt(1)).thenReturn(notVisibleInvalidTarget)
            whenever(it.getChildAt(2)).thenReturn(target)
        }
        fixture.window.mockDecorView<ViewGroup>(
            event = event,
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(container1)
            whenever(it.getChildAt(1)).thenReturn(container2)
        }

        val sut = fixture.getSut(target)

        sut.onSingleTapUp(event)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.click", it.category)
                assertEquals("user", it.type)
                assertEquals("test_button", it.data["view.id"])
                assertEquals("android.view.View", it.data["view.class"])
                assertEquals(INFO, it.level)
            }
        )
    }

    @Test
    fun `ignores invisible or gone views`() {
        val mockEvent = mock<MotionEvent>()
        val invalidTarget = mockView<View>(
            event = mockEvent,
            visible = false,
            clickable = true,
        )
        val validTarget = mockView<RadioButton>(
            event = mockEvent,
            clickable = true
        )
        fixture.window.mockDecorView<ViewGroup>(
            event = mockEvent,
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }

        val sut = fixture.getSut(validTarget, "radio_button")
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("radio_button", it.data["view.id"])
                assertEquals("android.widget.RadioButton", it.data["view.class"])
            }
        )
    }

    @Test
    fun `ignores not clickable targets`() {
        val mockEvent = mock<MotionEvent>()
        val invalidTarget = mockView<View>(
            event = mockEvent,
            clickable = false,
        )
        val validTarget = mockView<CheckBox>(
            event = mockEvent,
            clickable = true
        )
        fixture.window.mockDecorView<ViewGroup>(
            event = mockEvent
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }

        val sut = fixture.getSut(validTarget, "check_box")
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("check_box", it.data["view.id"])
                assertEquals("android.widget.CheckBox", it.data["view.class"])
            }
        )
    }

    @Test
    fun `when no children present and decor view not clickable, does not capture a breadcrumb`() {
        val mockEvent = mock<MotionEvent>()
        fixture.window.mockDecorView<ViewGroup>(
            event = mockEvent,
        ) {
            whenever(it.childCount).thenReturn(0)
        }

        val sut = fixture.getSut(mock())
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `when target is decorView, captures a breadcrumb for decorView`() {
        val mockEvent = mock<MotionEvent>()
        val decorView = fixture.window.mockDecorView<ViewGroup>(
            event = mockEvent,
            clickable = true
        ) {
            whenever(it.childCount).thenReturn(0)
        }

        val sut = fixture.getSut(decorView, "decor_view")
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(decorView.javaClass.canonicalName, it.data["view.class"])
                assertEquals("decor_view", it.data["view.id"])
            }
        )
    }

    @Test
    fun `does not capture breadcrumbs when view reference is null`() {
        val mockEvent = mock<MotionEvent>()

        val sut = fixture.getSut(mock())
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `uses simple class name if canonical name isn't available`() {
        val mockEvent = mock<MotionEvent>()

        class LocalView(context: Context) : View(context)

        val target = mockView<LocalView>(
            event = mockEvent,
            clickable = true
        )
        fixture.window.mockDecorView<ViewGroup>(
            event = mockEvent,
            touchWithinBounds = false,
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(target)
        }

        val sut = fixture.getSut(target)
        sut.onSingleTapUp(mockEvent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(target.javaClass.simpleName, it.data["view.class"])
            }
        )
    }
}
