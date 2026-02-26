package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.maskAllImages
import io.sentry.android.replay.maskAllText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ContainerMaskingOptionsTest {
  @BeforeTest
  fun setup() {
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
  }

  @Test
  fun `when maskAllText is set TextView in Unmask container is unmasked`() {
    buildActivity(MaskingOptionsActivity::class.java).setup()

    val options =
      SentryOptions().apply {
        sessionReplay.maskAllText = true
        sessionReplay.setUnmaskViewContainerClass(CustomUnmask::class.java.name)
      }

    val textNode =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.textViewInUnmask!!,
        null,
        0,
        options.sessionReplay,
      )
    assertFalse(textNode.shouldMask)
  }

  @Test
  fun `when maskAllImages is set ImageView in Unmask container is unmasked`() {
    buildActivity(MaskingOptionsActivity::class.java).setup()

    val options =
      SentryOptions().apply {
        sessionReplay.maskAllImages = true
        sessionReplay.setUnmaskViewContainerClass(CustomUnmask::class.java.name)
      }

    val imageNode =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.imageViewInUnmask!!,
        null,
        0,
        options.sessionReplay,
      )
    assertFalse(imageNode.shouldMask)
  }

  @Test
  fun `MaskContainer is always masked`() {
    buildActivity(MaskingOptionsActivity::class.java).setup()

    val options =
      SentryOptions().apply { sessionReplay.setMaskViewContainerClass(CustomMask::class.java.name) }

    val maskContainer =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.maskWithChildren!!,
        null,
        0,
        options.sessionReplay,
      )

    assertTrue(maskContainer.shouldMask)
  }

  @Test
  fun `when Views are in UnmaskContainer only direct children are unmasked`() {
    buildActivity(MaskingOptionsActivity::class.java).setup()

    val options =
      SentryOptions().apply {
        sessionReplay.addMaskViewClass(CustomView::class.java.name)
        sessionReplay.setUnmaskViewContainerClass(CustomUnmask::class.java.name)
      }

    val maskContainer =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.unmaskWithChildren!!,
        null,
        0,
        options.sessionReplay,
      )
    val firstChild =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.customViewInUnmask!!,
        maskContainer,
        0,
        options.sessionReplay,
      )
    val secondLevelChild =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.secondLayerChildInUnmask!!,
        firstChild,
        0,
        options.sessionReplay,
      )

    assertFalse(maskContainer.shouldMask)
    assertFalse(firstChild.shouldMask)
    assertTrue(secondLevelChild.shouldMask)
  }

  @Test
  fun `when MaskContainer is direct child of UnmaskContainer all children od Mask are masked`() {
    buildActivity(MaskingOptionsActivity::class.java).setup()

    val options =
      SentryOptions().apply {
        sessionReplay.setMaskViewContainerClass(CustomMask::class.java.name)
        sessionReplay.setUnmaskViewContainerClass(CustomUnmask::class.java.name)
      }

    val unmaskNode =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.unmaskWithMaskChild!!,
        null,
        0,
        options.sessionReplay,
      )
    val maskNode =
      ViewHierarchyNode.fromView(
        MaskingOptionsActivity.maskAsDirectChildOfUnmask!!,
        unmaskNode,
        0,
        options.sessionReplay,
      )

    assertFalse(unmaskNode.shouldMask)
    assertTrue(maskNode.shouldMask)
  }

  private class CustomView(context: Context) : View(context) {
    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      canvas.drawColor(Color.BLACK)
    }
  }

  private open class CustomGroup(context: Context) : LinearLayout(context) {
    init {
      setBackgroundColor(android.R.color.white)
      orientation = VERTICAL
      layoutParams = LayoutParams(100, 100)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      canvas.drawColor(Color.BLACK)
    }
  }

  private class CustomMask(context: Context) : CustomGroup(context)

  private class CustomUnmask(context: Context) : CustomGroup(context)

  private class MaskingOptionsActivity : Activity() {
    companion object {
      var unmaskWithTextView: ViewGroup? = null
      var textViewInUnmask: TextView? = null

      var unmaskWithImageView: ViewGroup? = null
      var imageViewInUnmask: ImageView? = null

      var unmaskWithChildren: ViewGroup? = null
      var customViewInUnmask: ViewGroup? = null
      var secondLayerChildInUnmask: View? = null

      var maskWithChildren: ViewGroup? = null

      var unmaskWithMaskChild: ViewGroup? = null
      var maskAsDirectChildOfUnmask: ViewGroup? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      val linearLayout =
        LinearLayout(this).apply {
          setBackgroundColor(android.R.color.white)
          orientation = LinearLayout.VERTICAL
          layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

      val context = this

      linearLayout.addView(
        CustomUnmask(context).apply {
          unmaskWithTextView = this
          this.addView(
            TextView(context).apply {
              textViewInUnmask = this
              text = "Hello, World!"
              layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
          )
        }
      )

      linearLayout.addView(
        CustomUnmask(context).apply {
          unmaskWithImageView = this
          this.addView(
            ImageView(context).apply {
              imageViewInUnmask = this
              val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!
              setImageDrawable(Drawable.createFromPath(image.path))
              layoutParams = LayoutParams(50, 50).apply { setMargins(0, 16, 0, 0) }
            }
          )
        }
      )

      linearLayout.addView(
        CustomUnmask(context).apply {
          unmaskWithChildren = this
          this.addView(
            CustomGroup(context).apply {
              customViewInUnmask = this
              this.addView(CustomView(context).apply { secondLayerChildInUnmask = this })
            }
          )
        }
      )

      linearLayout.addView(
        CustomMask(context).apply {
          maskWithChildren = this
          this.addView(CustomGroup(context).apply { this.addView(CustomView(context)) })
        }
      )

      linearLayout.addView(
        CustomUnmask(context).apply {
          unmaskWithMaskChild = this
          this.addView(CustomMask(context).apply { maskAsDirectChildOfUnmask = this })
        }
      )

      setContentView(linearLayout)
    }
  }
}
