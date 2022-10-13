package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.View
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ViewUtilsTest {

    @Test
    fun `getResourceId returns resourceId when available`() {
        val view = mock<View> {
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
    fun `getResourceId throws when resource id is not available`() {
        val view = mock<View> {
            whenever(it.id).doReturn(View.generateViewId())

            val context = mock<Context>()
            val resources = mock<Resources>()
            whenever(resources.getResourceEntryName(any())).doThrow(Resources.NotFoundException())
            whenever(context.resources).thenReturn(resources)
            whenever(it.context).thenReturn(context)
        }

        assertFailsWith<Resources.NotFoundException> { ViewUtils.getResourceId(view) }
    }

    @Test
    fun `getResourceIdWithFallback falls back to hexadecimal id when resource not found`() {
        val view = mock<View> {
            whenever(it.id).doReturn(1234)

            val context = mock<Context>()
            val resources = mock<Resources>()
            whenever(resources.getResourceEntryName(it.id)).thenThrow(Resources.NotFoundException())
            whenever(context.resources).thenReturn(resources)
            whenever(it.context).thenReturn(context)
        }

        assertEquals(ViewUtils.getResourceIdWithFallback(view), "0x4d2")
    }
}
