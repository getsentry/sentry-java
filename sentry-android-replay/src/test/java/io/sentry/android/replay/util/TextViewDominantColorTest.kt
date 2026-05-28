package io.sentry.android.replay.util

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class TextViewDominantColorTest {
  @Test
  fun `when no spans, returns currentTextColor`() {
    val controller = buildActivity(TextViewActivity::class.java, null).setup()
    controller.create().start().resume()

    TextViewActivity.textView?.setTextColor(Color.WHITE)

    val node =
      ViewHierarchyNode.fromView(
        TextViewActivity.textView!!,
        null,
        0,
        SentryOptions().sessionReplay,
      )
    assertTrue(node is TextViewHierarchyNode)
    assertNull(node.layout?.dominantTextColor)
  }

  @Test
  fun `when has a foreground color span, returns its color`() {
    val controller = buildActivity(TextViewActivity::class.java, null).setup()
    controller.create().start().resume()

    val text = "Hello, World!"
    TextViewActivity.textView?.text =
      SpannableString(text).apply {
        setSpan(ForegroundColorSpan(Color.RED), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
      }
    TextViewActivity.textView?.setTextColor(Color.WHITE)
    TextViewActivity.textView?.requestLayout()

    shadowOf(Looper.getMainLooper()).idle()

    val node =
      ViewHierarchyNode.fromView(
        TextViewActivity.textView!!,
        null,
        0,
        SentryOptions().sessionReplay,
      )
    assertTrue(node is TextViewHierarchyNode)
    assertEquals(Color.RED, node.layout?.dominantTextColor)
  }

  @Test
  fun `when has multiple foreground color spans, returns color of the longest span`() {
    val controller = buildActivity(TextViewActivity::class.java, null).setup()
    controller.create().start().resume()

    val text = "Hello, World!"
    TextViewActivity.textView?.text =
      SpannableString(text).apply {
        setSpan(ForegroundColorSpan(Color.RED), 0, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        setSpan(ForegroundColorSpan(Color.BLACK), 6, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
      }
    TextViewActivity.textView?.setTextColor(Color.WHITE)
    TextViewActivity.textView?.requestLayout()

    shadowOf(Looper.getMainLooper()).idle()

    val node =
      ViewHierarchyNode.fromView(
        TextViewActivity.textView!!,
        null,
        0,
        SentryOptions().sessionReplay,
      )
    assertTrue(node is TextViewHierarchyNode)
    assertEquals(Color.BLACK, node.layout?.dominantTextColor)
  }
}

private class TextViewActivity : Activity() {
  companion object {
    var textView: TextView? = null
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

    setContentView(linearLayout)
  }
}
