package io.sentry.android.replay.util

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class TextViewDominantColorTest {

    @Test
    fun `when no spans, returns currentTextColor`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        textView.text = "Hello, World!"
        textView.setTextColor(Color.WHITE)

        assertEquals(Color.WHITE, textView.dominantTextColor)
    }

    @Test
    fun `when has a foreground color span, returns its color`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        val text = "Hello, World!"
        textView.text = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.RED), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        textView.setTextColor(Color.WHITE)

        assertEquals(Color.RED, textView.dominantTextColor)
    }

    @Test
    fun `when has multiple foreground color spans, returns color of the longest span`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        val text = "Hello, World!"
        textView.text = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.RED), 0, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            setSpan(ForegroundColorSpan(Color.BLACK), 6, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        textView.setTextColor(Color.WHITE)

        assertEquals(Color.BLACK, textView.dominantTextColor)
    }
}
