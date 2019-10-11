package io.sentry.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class DefaultAndroidEventProcessorTest {
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when instance is created, application context reference is stored`() {
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }
        val sut = DefaultAndroidEventProcessor(mockContext)

        assertEquals(sut.context, context)
    }

    @Test
    fun `when null context is provided, invalid argument is thrown`() {
        assertFailsWith<IllegalArgumentException> { DefaultAndroidEventProcessor(null) }
    }
}
