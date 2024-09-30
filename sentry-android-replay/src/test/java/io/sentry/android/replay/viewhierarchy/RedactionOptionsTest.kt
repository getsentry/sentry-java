package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.redactAllImages
import io.sentry.android.replay.redactAllText
import io.sentry.android.replay.sentryReplayIgnore
import io.sentry.android.replay.sentryReplayRedact
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class RedactionOptionsTest {

    @BeforeTest
    fun setup() {
        System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    }

    @Test
    fun `when redactAllText is set all TextView nodes are redacted`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(ExampleActivity.radioButton!!, null, 0, options)

        assertTrue(textNode is TextViewHierarchyNode)
        assertTrue(textNode.shouldRedact)

        assertTrue(radioButtonNode is TextViewHierarchyNode)
        assertTrue(radioButtonNode.shouldRedact)
    }

    @Test
    fun `when redactAllText is set to false all TextView nodes are ignored`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = false
        }

        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(ExampleActivity.radioButton!!, null, 0, options)

        assertTrue(textNode is TextViewHierarchyNode)
        assertFalse(textNode.shouldRedact)

        assertTrue(radioButtonNode is TextViewHierarchyNode)
        assertFalse(radioButtonNode.shouldRedact)
    }

    @Test
    fun `when redactAllImages is set all ImageView nodes are redacted`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllImages = true
        }

        val imageNode = ViewHierarchyNode.fromView(ExampleActivity.imageView!!, null, 0, options)

        assertTrue(imageNode is ImageViewHierarchyNode)
        assertTrue(imageNode.shouldRedact)
    }

    @Test
    fun `when redactAllImages is set to false all ImageView nodes are ignored`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllImages = false
        }

        val imageNode = ViewHierarchyNode.fromView(ExampleActivity.imageView!!, null, 0, options)

        assertTrue(imageNode is ImageViewHierarchyNode)
        assertFalse(imageNode.shouldRedact)
    }

    @Test
    fun `when sentry-redact tag is set redacts the view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = false
        }

        ExampleActivity.textView!!.tag = "sentry-redact"
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)

        assertTrue(textNode.shouldRedact)
    }

    @Test
    fun `when sentry-ignore tag is set ignores the view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        ExampleActivity.textView!!.tag = "sentry-ignore"
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldRedact)
    }

    @Test
    fun `when sentry-privacy tag is set to redact redacts the view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = false
        }

        ExampleActivity.textView!!.sentryReplayRedact()
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)

        assertTrue(textNode.shouldRedact)
    }

    @Test
    fun `when sentry-privacy tag is set to ignore ignores the view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        ExampleActivity.textView!!.sentryReplayIgnore()
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldRedact)
    }

    @Test
    fun `when view is not visible, does not redact the view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        ExampleActivity.textView!!.visibility = View.GONE
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldRedact)
    }

    @Test
    fun `when added to redact list redacts custom view`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactViewClasses.add(CustomView::class.java.canonicalName)
        }

        val customViewNode = ViewHierarchyNode.fromView(ExampleActivity.customView!!, null, 0, options)

        assertTrue(customViewNode.shouldRedact)
    }

    @Test
    fun `when subclass is added to ignored classes ignores all instances of that class`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true // all TextView subclasses
            experimental.sessionReplay.ignoreViewClasses.add(RadioButton::class.java.canonicalName)
        }

        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(ExampleActivity.radioButton!!, null, 0, options)

        assertTrue(textNode.shouldRedact)
        assertFalse(radioButtonNode.shouldRedact)
    }

    @Test
    fun `when a container view is ignored its children are not ignored`() {
        buildActivity(ExampleActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.ignoreViewClasses.add(LinearLayout::class.java.canonicalName)
        }

        val linearLayoutNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!.parent as LinearLayout, null, 0, options)
        val textNode = ViewHierarchyNode.fromView(ExampleActivity.textView!!, null, 0, options)
        val imageNode = ViewHierarchyNode.fromView(ExampleActivity.imageView!!, null, 0, options)

        assertFalse(linearLayoutNode.shouldRedact)
        assertTrue(textNode.shouldRedact)
        assertTrue(imageNode.shouldRedact)
    }
}

private class CustomView(context: Context) : View(context) {

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
    }
}

private class ExampleActivity : Activity() {

    companion object {
        var textView: TextView? = null
        var radioButton: RadioButton? = null
        var imageView: ImageView? = null
        var customView: CustomView? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val linearLayout = LinearLayout(this).apply {
            setBackgroundColor(android.R.color.white)
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        textView = TextView(this).apply {
            text = "Hello, World!"
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        linearLayout.addView(textView)

        val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!
        imageView = ImageView(this).apply {
            setImageDrawable(Drawable.createFromPath(image.path))
            layoutParams = LayoutParams(50, 50).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        linearLayout.addView(imageView)

        radioButton = RadioButton(this).apply {
            text = "Radio Button"
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        linearLayout.addView(radioButton)

        customView = CustomView(this).apply {
            layoutParams = LayoutParams(50, 50).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        linearLayout.addView(customView)

        setContentView(linearLayout)
    }
}
