package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.ILogger
import io.sentry.core.SentryLevel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestMetadataReaderTest {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `isAutoInit won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val logger = mock<ILogger>()
        assertTrue(ManifestMetadataReader.isAutoInit(context, logger))
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val options = SentryAndroidOptions()

        ManifestMetadataReader.applyMetadata(context, options)
        val logger = mock<ILogger>()
        options.setLogger(logger)
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }
}
