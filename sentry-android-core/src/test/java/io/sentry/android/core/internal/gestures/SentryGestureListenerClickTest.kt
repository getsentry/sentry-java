package io.sentry.android.core.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.RadioButton
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.PropagationContext
import io.sentry.Scope.IWithPropagationContext
import io.sentry.ScopeCallback
import io.sentry.SentryLevel.INFO
import io.sentry.android.core.SentryAndroidOptions
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryGestureListenerClickTest {
    class Fixture {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val context = mock<Context>()
        val resources = mock<Resources>()
        val options = SentryAndroidOptions().apply {
            isEnableUserInteractionBreadcrumbs = true
            isEnableUserInteractionTracing = true
            gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(true))
            dsn = "https://key@sentry.io/proj"
        }
        val scopes = mock<IScopes>()
        val scope = mock<IScope>()
        val propagationContext = PropagationContext()
        lateinit var target: View
        lateinit var invalidTarget: View

        internal inline fun <reified T : View> getSut(
            event: MotionEvent,
            resourceName: String = "test_button",
            isInvalidTargetVisible: Boolean = true,
            isInvalidTargetClickable: Boolean = true,
            attachViewsToRoot: Boolean = true,
            targetOverride: View? = null
        ): SentryGestureListener {
            invalidTarget = mockView(
                event = event,
                visible = isInvalidTargetVisible,
                clickable = isInvalidTargetClickable,
                context = context
            )

            if (targetOverride == null) {
                this.target = mockView<T>(
                    event = event,
                    clickable = true,
                    context = context
                )
            } else {
                this.target = targetOverride
            }

            if (attachViewsToRoot) {
                window.mockDecorView<ViewGroup>(
                    event = event,
                    context = context
                ) {
                    whenever(it.childCount).thenReturn(2)
                    whenever(it.getChildAt(0)).thenReturn(invalidTarget)
                    whenever(it.getChildAt(1)).thenReturn(target)
                }
            }

            resources.mockForTarget(this.target, resourceName)
            whenever(context.resources).thenReturn(resources)
            whenever(this.target.context).thenReturn(context)
            whenever(activity.window).thenReturn(window)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(any())
            doAnswer { (it.arguments[0] as IWithPropagationContext).accept(propagationContext); propagationContext; }.whenever(scope).withPropagationContext(any())
            return SentryGestureListener(
                activity,
                scopes,
                options
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when target and its ViewGroup are clickable, captures a breadcrumb for target`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<View>(
            event,
            isInvalidTargetVisible = false,
            attachViewsToRoot = false
        )

        val container1 = mockView<ViewGroup>(event = event, touchWithinBounds = false, context = fixture.context)
        val notClickableInvalidTarget = mockView<View>(event = event)
        val container2 = mockView<ViewGroup>(event = event, clickable = true, context = fixture.context) {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(notClickableInvalidTarget)
            whenever(it.getChildAt(1)).thenReturn(fixture.invalidTarget)
            whenever(it.getChildAt(2)).thenReturn(fixture.target)
        }
        fixture.window.mockDecorView<ViewGroup>(event = event, context = fixture.context) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(container1)
            whenever(it.getChildAt(1)).thenReturn(container2)
        }

        sut.onSingleTapUp(event)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.click", it.category)
                assertEquals("user", it.type)
                assertEquals("test_button", it.data["view.id"])
                assertEquals("android.view.View", it.data["view.class"])
                assertEquals(INFO, it.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `ignores invisible or gone views`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<RadioButton>(
            event,
            "radio_button",
            isInvalidTargetVisible = false
        )

        sut.onSingleTapUp(event)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("radio_button", it.data["view.id"])
                assertEquals("android.widget.RadioButton", it.data["view.class"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `ignores not clickable targets`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<CheckBox>(
            event,
            "check_box",
            isInvalidTargetClickable = false
        )

        sut.onSingleTapUp(event)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("check_box", it.data["view.id"])
                assertEquals("android.widget.CheckBox", it.data["view.class"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `when no children present and decor view not clickable, does not capture a breadcrumb`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<View>(event, attachViewsToRoot = false)
        fixture.window.mockDecorView<ViewGroup>(event = event) {
            whenever(it.childCount).thenReturn(0)
        }

        sut.onSingleTapUp(event)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `when target is decorView, captures a breadcrumb for decorView`() {
        val event = mock<MotionEvent>()
        val decorView = fixture.window.mockDecorView<ViewGroup>(event = event, clickable = true) {
            whenever(it.childCount).thenReturn(0)
        }

        val sut = fixture.getSut<ViewGroup>(event, "decor_view", targetOverride = decorView)
        sut.onSingleTapUp(event)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(decorView.javaClass.canonicalName, it.data["view.class"])
                assertEquals("decor_view", it.data["view.id"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not capture breadcrumbs when view reference is null`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<View>(event, attachViewsToRoot = false)

        sut.onSingleTapUp(event)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `uses simple class name if canonical name isn't available`() {
        class LocalView(context: Context) : View(context)

        val event = mock<MotionEvent>()
        val sut = fixture.getSut<LocalView>(event, attachViewsToRoot = false)
        fixture.window.mockDecorView<ViewGroup>(event = event, touchWithinBounds = true) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(fixture.target)
        }

        sut.onSingleTapUp(event)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(fixture.target.javaClass.simpleName, it.data["view.class"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `creates new trace on click`() {
        class LocalView(context: Context) : View(context)

        val event = mock<MotionEvent>()
        val sut = fixture.getSut<LocalView>(event, attachViewsToRoot = false)
        fixture.window.mockDecorView<ViewGroup>(event = event, touchWithinBounds = true) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(fixture.target)
        }

        sut.onSingleTapUp(event)

        verify(fixture.scope).propagationContext = any()
    }

    @Test
    fun `if touch is not within view group bounds does not traverse its children`() {
        val event = mock<MotionEvent>()
        val sut = fixture.getSut<View>(event, attachViewsToRoot = false)
        fixture.window.mockDecorView<ViewGroup>(event = event, touchWithinBounds = false) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(fixture.target)
        }

        sut.onSingleTapUp(event)

        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }
}
