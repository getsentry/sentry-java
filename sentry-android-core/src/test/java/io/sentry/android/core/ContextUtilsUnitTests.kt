package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.NoOpLogger
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ContextUtilsUnitTests {

    private lateinit var context: Context
    private lateinit var logger: ILogger

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        logger = NoOpLogger.getInstance()
    }

    @Test
    fun `Given a valid context, returns a valid PackageInfo`() {
        val packageInfo = ContextUtils.getPackageInfo(context, mock(), mock())
        assertNotNull(packageInfo)
    }

    @Test
    fun `Given an  invalid context, do not throw Error`() {
        // as Context is not fully mocked, it'll throw NPE but catch it and return null
        val packageInfo = ContextUtils.getPackageInfo(mock(), mock(), mock())
        assertNull(packageInfo)
    }

    @Test
    fun `Given a valid PackageInfo, returns a valid versionCode`() {
        val packageInfo = ContextUtils.getPackageInfo(context, mock(), mock())
        val versionCode = ContextUtils.getVersionCode(packageInfo!!, mock())

        assertNotNull(versionCode)
    }

    @Test
    fun `Given a valid PackageInfo, returns a valid versionName`() {
        // VersionName is null during tests, so we mock it the second time
        val packageInfo = ContextUtils.getPackageInfo(context, mock(), mock())!!
        val versionName = ContextUtils.getVersionName(packageInfo)
        assertNull(versionName)
        val mockedPackageInfo = spy(packageInfo) { it.versionName = "" }
        val mockedVersionName = ContextUtils.getVersionName(mockedPackageInfo)
        assertNotNull(mockedVersionName)
    }
}
