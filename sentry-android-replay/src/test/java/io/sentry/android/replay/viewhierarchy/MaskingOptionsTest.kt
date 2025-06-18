package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.maskAllImages
import io.sentry.android.replay.maskAllText
import io.sentry.android.replay.sentryReplayMask
import io.sentry.android.replay.sentryReplayUnmask
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class MaskingOptionsTest {
    @BeforeTest
    fun setup() {
        System.setProperty("robolectric.areWindowsMarkedVisible", "true")
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
    }

    @Test
    fun `when maskAllText is set all TextView nodes are masked`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = true
            }

        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.radioButton!!, null, 0, options)

        assertTrue(textNode is TextViewHierarchyNode)
        assertTrue(textNode.shouldMask)

        assertTrue(radioButtonNode is TextViewHierarchyNode)
        assertTrue(radioButtonNode.shouldMask)
    }

    @Test
    fun `when maskAllText is set to false all TextView nodes are unmasked`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = false
            }

        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.radioButton!!, null, 0, options)

        assertTrue(textNode is TextViewHierarchyNode)
        assertFalse(textNode.shouldMask)

        assertTrue(radioButtonNode is TextViewHierarchyNode)
        assertFalse(radioButtonNode.shouldMask)
    }

    @Test
    fun `when maskAllImages is set all ImageView nodes are masked`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllImages = true
            }

        val imageNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.imageView!!, null, 0, options)

        assertTrue(imageNode is ImageViewHierarchyNode)
        assertTrue(imageNode.shouldMask)
    }

    @Test
    fun `when maskAllImages is set to false all ImageView nodes are unmasked`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllImages = false
            }

        val imageNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.imageView!!, null, 0, options)

        assertTrue(imageNode is ImageViewHierarchyNode)
        assertFalse(imageNode.shouldMask)
    }

    @Test
    fun `when sentry-mask tag is set mask the view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = false
            }

        MaskingOptionsActivity.textView!!.tag = "sentry-mask"
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)

        assertTrue(textNode.shouldMask)
    }

    @Test
    fun `when sentry-unmask tag is set unmasks the view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = true
            }

        MaskingOptionsActivity.textView!!.tag = "sentry-unmask"
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldMask)
    }

    @Test
    fun `when sentry-privacy tag is set to mask masks the view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = false
            }

        MaskingOptionsActivity.textView!!.sentryReplayMask()
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)

        assertTrue(textNode.shouldMask)
    }

    @Test
    fun `when sentry-privacy tag is set to unmask unmasks the view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = true
            }

        MaskingOptionsActivity.textView!!.sentryReplayUnmask()
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldMask)
    }

    @Test
    fun `when view is not visible, does not mask the view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = true
            }

        MaskingOptionsActivity.textView!!.visibility = View.GONE
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)

        assertFalse(textNode.shouldMask)
    }

    @Test
    fun `when added to mask list masks custom view`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskViewClasses.add(CustomView::class.java.canonicalName)
            }

        val customViewNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.customView!!, null, 0, options)

        assertTrue(customViewNode.shouldMask)
    }

    @Test
    fun `when subclass is added to ignored classes ignores all instances of that class`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.maskAllText = true // all TextView subclasses
                sessionReplay.unmaskViewClasses.add(RadioButton::class.java.canonicalName)
            }

        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)
        val radioButtonNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.radioButton!!, null, 0, options)

        assertTrue(textNode.shouldMask)
        assertFalse(radioButtonNode.shouldMask)
    }

    @Test
    fun `when a container view is ignored its children are not ignored`() {
        buildActivity(MaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options =
            SentryOptions().apply {
                sessionReplay.unmaskViewClasses.add(LinearLayout::class.java.canonicalName)
            }

        val linearLayoutNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!.parent as LinearLayout, null, 0, options)
        val textNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.textView!!, null, 0, options)
        val imageNode = ViewHierarchyNode.fromView(MaskingOptionsActivity.imageView!!, null, 0, options)

        assertFalse(linearLayoutNode.shouldMask)
        assertTrue(textNode.shouldMask)
        assertTrue(imageNode.shouldMask)
    }
}

private class CustomView(
    context: Context,
) : View(context) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
    }
}

private class MaskingOptionsActivity : Activity() {
    companion object {
        var textView: TextView? = null
        var radioButton: RadioButton? = null
        var imageView: ImageView? = null
        var customView: CustomView? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val linearLayout =
            LinearLayout(this).apply {
                setBackgroundColor(android.R.color.white)
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }

        textView =
            TextView(this).apply {
                text = "Hello, World!"
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
        linearLayout.addView(textView)

        val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!
        imageView =
            ImageView(this).apply {
                setImageDrawable(Drawable.createFromPath(image.path))
                layoutParams =
                    LayoutParams(50, 50).apply {
                        setMargins(0, 16, 0, 0)
                    }
            }
        linearLayout.addView(imageView)

        radioButton =
            RadioButton(this).apply {
                text = "Radio Button"
                layoutParams =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 16, 0, 0)
                    }
            }
        linearLayout.addView(radioButton)

        customView =
            CustomView(this).apply {
                layoutParams =
                    LayoutParams(50, 50).apply {
                        setMargins(0, 16, 0, 0)
                    }
            }
        linearLayout.addView(customView)

        setContentView(linearLayout)
    }
}
