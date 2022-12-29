package io.sentry.android.core

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalStateException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ViewHierarchyEventProcessorTest {
    private class Fixture {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val view = mock<View>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }

        init {
            whenever(view.width).thenReturn(1)
            whenever(view.height).thenReturn(1)
            whenever(window.decorView).thenReturn(view)
            whenever(window.peekDecorView()).thenReturn(view)
            whenever(activity.window).thenReturn(window)

            CurrentActivityHolder.getInstance().setActivity(activity)
        }

        fun getSut(attachViewHierarchy: Boolean = false): ViewHierarchyEventProcessor {
            options.isAttachViewHierarchy = attachViewHierarchy
            return ViewHierarchyEventProcessor(options)
        }

        fun process(
            attachViewHierarchy: Boolean,
            event: SentryEvent
        ): Pair<SentryEvent, Hint> {
            val processor = getSut(attachViewHierarchy)
            val hint = Hint()
            processor.process(event, hint)

            return Pair(event, hint)
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        fixture = Fixture()
    }

    @Test
    fun `when an event errored, the view hierarchy should not attached if the feature is disabled`() {
        val (event, hint) = fixture.process(
            false,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNotNull(event)
        assertNull(hint.viewHierarchy)
    }

    @Test
    fun `when an event errored, the view hierarchy should be attached if the feature is enabled`() {
        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNotNull(event)
        assertNotNull(hint.viewHierarchy)
    }

    @Test
    fun `when an event did not error, the view hierarchy should be attached if the feature is enabled`() {
        val (event, hint) = fixture.process(
            false,
            SentryEvent(null)
        )

        assertNotNull(event)
        assertNull(hint.viewHierarchy)
    }

    @Test
    fun `when there's no current activity the viewhierarchy is null`() {
        CurrentActivityHolder.getInstance().clearActivity()

        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNotNull(event)
        assertNull(hint.viewHierarchy)
    }

    @Test
    fun `when retrieving the viewHierarchy crashes no view hierarchy is collected`() {
        whenever(fixture.view.width).thenThrow(IllegalStateException("invalid ui state"))
        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNotNull(event)
        assertNull(hint.viewHierarchy)
    }

    @Test
    fun `snapshot of android view is properly created`() {
        val content = mockedView(
            0.0f,
            1.0f,
            200,
            400,
            1f,
            View.VISIBLE,
            listOf(
                mockedView(10.0f, 11.0f, 100, 101, 0.5f, View.VISIBLE),
                mockedView(20.0f, 21.0f, 200, 201, 1f, View.INVISIBLE)
            )
        )

        val activity = mock<Activity>()
        val window = mock<Window>()
        whenever(window.decorView).thenReturn(content)
        whenever(window.peekDecorView()).thenReturn(content)
        whenever(activity.window).thenReturn(window)

        val viewHierarchy = ViewHierarchyEventProcessor.snapshotViewHierarchy(activity)
        assertEquals("android_view_system", viewHierarchy.renderingSystem)
        assertEquals(1, viewHierarchy.windows!!.size)

        val contentNode = viewHierarchy.windows!![0]
        assertEquals(200.0, contentNode.width)
        assertEquals(400.0, contentNode.height)
        assertEquals(0.0, contentNode.x)
        assertEquals(1.0, contentNode.y)
        assertEquals(true, contentNode.visible)
        assertEquals(2, contentNode.children!!.size)

        contentNode.children!![0].apply {
            assertEquals(100.0, width)
            assertEquals(101.0, height)
            assertEquals(10.0, x)
            assertEquals(11.0, y)
            assertEquals(true, visible)
            assertEquals(null, children)
        }

        contentNode.children!![1].apply {
            assertEquals(200.0, width)
            assertEquals(201.0, height)
            assertEquals(20.0, x)
            assertEquals(21.0, y)
            assertEquals(false, visible)
            assertEquals(null, children)
        }
    }

    private fun mockedView(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        alpha: Float,
        visibility: Int,
        children: List<View> = emptyList()
    ): View {
        val view = mock<ViewGroup>()

        whenever(view.x).thenReturn(x)
        whenever(view.y).thenReturn(y)
        whenever(view.width).thenReturn(width)
        whenever(view.height).thenReturn(height)
        whenever(view.alpha).thenReturn(alpha)
        whenever(view.visibility).thenReturn(visibility)
        whenever(view.childCount).thenReturn(children.size)

        for (i in children.indices) {
            whenever(view.getChildAt(i)).thenReturn(children[i])
        }

        return view
    }
}
