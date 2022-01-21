package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.View
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.android.core.internal.gestures.ViewUtils
import org.junit.Test
import kotlin.test.assertEquals

class ViewUtilsTest {

    @Test
    fun `getResourceId returns resourceId when available`() {
        val view: View = mock {
            whenever(it.id).doReturn(View.generateViewId())

            val context = mock<Context>()
            val resources = mock<Resources>()
            whenever(resources.getResourceEntryName(it.id)).thenReturn("test_view")
            whenever(context.resources).thenReturn(resources)
            whenever(it.context).thenReturn(context)
        }

        assertEquals(ViewUtils.getResourceId(view), "test_view")
    }

    @Test
    fun `getResourceId falls back to hexadecimal id when resource not found`() {
        val view: View = mock {
            whenever(it.id).doReturn(1234)

            val context = mock<Context>()
            val resources = mock<Resources>()
            whenever(resources.getResourceEntryName(it.id)).thenThrow(Resources.NotFoundException())
            whenever(context.resources).thenReturn(resources)
            whenever(it.context).thenReturn(context)
        }

        assertEquals(ViewUtils.getResourceId(view), "0x4d2")
    }
}
