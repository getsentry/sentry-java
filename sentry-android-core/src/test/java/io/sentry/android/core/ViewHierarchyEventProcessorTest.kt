package io.sentry.android.core

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.TypeCheckHint
import io.sentry.protocol.SentryException
import io.sentry.util.thread.IThreadChecker
import org.junit.runner.RunWith
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.Writer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ViewHierarchyEventProcessorTest {
    private class Fixture {
        val logger = mock<AndroidLogger>()
        val serializer: JsonSerializer = mock {
            on(it.serialize(any<JsonSerializable>(), any())).then { invocationOnMock: InvocationOnMock ->
                val writer: Writer = invocationOnMock.getArgument(1)
                writer.write("mock-data")
                writer.flush()
            }
        }
        val emptySerializer: JsonSerializer = mock {
            on(it.serialize(any<JsonSerializable>(), any())).then { invocationOnMock: InvocationOnMock ->
                val writer: Writer = invocationOnMock.getArgument(1)
                writer.flush()
            }
        }
        val activity = mock<Activity>()
        val threadChecker = mock<IThreadChecker>()
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
            whenever(activity.runOnUiThread(any())).then {
                it.getArgument<Runnable>(0).run()
            }
            whenever(threadChecker.isMainThread).thenReturn(true)

            CurrentActivityHolder.getInstance().setActivity(activity)
        }

        fun getSut(attachViewHierarchy: Boolean = false): ViewHierarchyEventProcessor {
            options.isAttachViewHierarchy = attachViewHierarchy
            options.threadChecker = threadChecker
            return ViewHierarchyEventProcessor(options)
        }

        fun process(
            attachViewHierarchy: Boolean,
            event: SentryEvent,
            hint: Hint = Hint()
        ): Pair<SentryEvent, Hint> {
            val processor = getSut(attachViewHierarchy)
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
    fun `should return a view hierarchy as byte array`() {
        val viewHierarchy = ViewHierarchyEventProcessor.snapshotViewHierarchyAsData(
            fixture.activity,
            fixture.threadChecker,
            fixture.serializer,
            fixture.logger
        )

        assertNotNull(viewHierarchy)
        assertFalse(viewHierarchy.isEmpty())
    }

    @Test
    fun `should return null as bytes are empty array`() {
        val viewHierarchy = ViewHierarchyEventProcessor.snapshotViewHierarchyAsData(
            fixture.activity,
            fixture.threadChecker,
            fixture.emptySerializer,
            fixture.logger
        )

        assertNull(viewHierarchy)
    }

    @Test
    fun `when an event errored, the view hierarchy should not attached if the event is from hybrid sdk`() {
        val hintFromHybridSdk = Hint()
        hintFromHybridSdk.set(TypeCheckHint.SENTRY_IS_FROM_HYBRID_SDK, true)
        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            },
            hintFromHybridSdk
        )

        assertNotNull(event)
        assertNull(hint.viewHierarchy)
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
    fun `when an event errored in the background, the view hierarchy should captured on the main thread`() {
        whenever(fixture.threadChecker.isMainThread).thenReturn(false)

        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        verify(fixture.activity).runOnUiThread(any())
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
    fun `when there's no current activity the view hierarchy is null`() {
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
    fun `when there's no current window the view hierarchy is null`() {
        whenever(fixture.activity.window).thenReturn(null)

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
    fun `when there's no current decor view the view hierarchy is null`() {
        whenever(fixture.window.peekDecorView()).thenReturn(null)

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
    fun `when retrieving the view hierarchy crashes no view hierarchy is collected`() {
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
                mockedView(10.0f, 11.0f, 100, 101, 0.5f, View.GONE),
                mockedView(20.0f, 21.0f, 200, 201, 1f, View.INVISIBLE)
            )
        )

        val viewHierarchy = ViewHierarchyEventProcessor.snapshotViewHierarchy(content)
        assertEquals("android_view_system", viewHierarchy.renderingSystem)
        assertEquals(1, viewHierarchy.windows!!.size)

        val contentNode = viewHierarchy.windows!![0]
        assertEquals(200.0, contentNode.width)
        assertEquals(400.0, contentNode.height)
        assertEquals(0.0, contentNode.x)
        assertEquals(1.0, contentNode.y)
        assertEquals("visible", contentNode.visibility)
        assertEquals(2, contentNode.children!!.size)

        contentNode.children!![0].apply {
            assertEquals(100.0, width)
            assertEquals(101.0, height)
            assertEquals(10.0, x)
            assertEquals(11.0, y)
            assertEquals("gone", visibility)
            assertEquals(null, children)
        }

        contentNode.children!![1].apply {
            assertEquals(200.0, width)
            assertEquals(201.0, height)
            assertEquals(20.0, x)
            assertEquals(21.0, y)
            assertEquals("invisible", visibility)
            assertEquals(null, children)
        }
    }

    @Test
    fun `when enabled, the feature is added to the integration list`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        val (event, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )
        assertTrue(fixture.options.sdkVersion!!.integrationSet.contains("ViewHierarchy"))
    }

    @Test
    fun `when not enabled, the feature is not added to the integration list`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        val (event, hint) = fixture.process(
            false,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )
        assertFalse(fixture.options.sdkVersion!!.integrationSet.contains("ViewHierarchy"))
    }

    @Test
    fun `when view hierarchies are captured rapidly, capturing should be debounced`() {
        val processor = fixture.getSut(true)
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        var hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.viewHierarchy)
        hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.viewHierarchy)
        hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.viewHierarchy)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertNull(hint1.viewHierarchy)
    }

    @Test
    fun `when view hierarchies are captured rapidly, debounced flag should be propagated`() {
        val processor = fixture.getSut(true)

        var debounceFlag = false
        fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, debounce ->
            debounceFlag = debounce
            true
        }
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint0 = Hint()
        processor.process(event, hint0)
        assertFalse(debounceFlag)
        processor.process(event, hint0)
        assertFalse(debounceFlag)
        processor.process(event, hint0)
        assertFalse(debounceFlag)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertTrue(debounceFlag)
    }

    @Test
    fun `when view hierarchies are captured rapidly, capture callback can still overrule debouncing`() {
        val processor = fixture.getSut(true)

        fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, _ ->
            true
        }
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint0 = Hint()
        processor.process(event, hint0)
        processor.process(event, hint0)
        processor.process(event, hint0)
        assertNotNull(hint0.viewHierarchy)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertNotNull(hint1.viewHierarchy)
    }

    @Test
    fun `when capture callback returns false, no view hierarchy should be captured`() {
        fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, _ ->
            false
        }
        val (_, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNull(hint.viewHierarchy)
    }

    @Test
    fun `when capture callback returns true, a view hierarchy should be captured`() {
        fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, _ ->
            true
        }
        val (_, hint) = fixture.process(
            true,
            SentryEvent().apply {
                exceptions = listOf(SentryException())
            }
        )

        assertNotNull(hint.viewHierarchy)
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
