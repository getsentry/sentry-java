package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextUtilsUnitTests {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `Given a valid context, returns a valid PackageIndo`() {
        val packageInfo = ContextUtils.getPackageInfo(context, mock())
        assertNotNull(packageInfo)
    }

    @Test
    fun `Given an  invalid context, do not throw Error`() {
        // as Context is not fully mocked, it'll throw NPE but catch it and return null
        val packageInfo = ContextUtils.getPackageInfo(mock(), mock())
        assertNull(packageInfo)
    }

    @Test
    fun `Given a valid PackageInfo, returns a valid versionCode`() {
        val packageInfo = ContextUtils.getPackageInfo(context, mock())
        val versionCode = ContextUtils.getVersionCode(packageInfo!!)

        assertNotNull(versionCode)
    }
}
