package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.NoOpLogger
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowBuild
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Config(sdk = [33])
@RunWith(AndroidJUnit4::class)
class ContextUtilsTest {

    private lateinit var shadowActivityManager: ShadowActivityManager
    private lateinit var context: Context
    private lateinit var logger: ILogger

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        logger = NoOpLogger.getInstance()
        ShadowBuild.reset()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        shadowActivityManager = Shadow.extract(activityManager)
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

    @Test
    fun `when context is valid, getApplicationName returns application name`() {
        val appName = ContextUtils.getApplicationName(context, logger)
        assertEquals("io.sentry.android.core.test", appName)
    }

    @Test
    fun `when context is invalid, getApplicationName returns null`() {
        val appName = ContextUtils.getApplicationName(mock(), logger)
        assertNull(appName)
    }

    @Test
    fun `isSideLoaded returns true for test context`() {
        val sideLoadedInfo =
            ContextUtils.retrieveSideLoadedInfo(context, logger, BuildInfoProvider(logger))
        assertTrue(sideLoadedInfo!!.isSideLoaded)
    }

    @Test
    fun `when installerPackageName is not null, sideLoadedInfo returns false and installerStore`() {
        val mockedContext = spy(context) {
            val mockedPackageManager = spy(mock.packageManager) {
                whenever(mock.getInstallerPackageName(any())).thenReturn("play.google.com")
            }
            whenever(mock.packageManager).thenReturn(mockedPackageManager)
        }
        val sideLoadedInfo =
            ContextUtils.retrieveSideLoadedInfo(mockedContext, logger, BuildInfoProvider(logger))
        assertFalse(sideLoadedInfo!!.isSideLoaded)
        assertEquals("play.google.com", sideLoadedInfo.installerStore)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun `when display metrics specified, getDisplayMetrics returns correct values`() {
        val displayMetrics = ContextUtils.getDisplayMetrics(context, logger)
        assertEquals(1080, displayMetrics!!.widthPixels)
        assertEquals(1920, displayMetrics.heightPixels)
        assertEquals(3.0f, displayMetrics.density)
        assertEquals(480, displayMetrics.densityDpi)
    }

    @Test
    fun `when display metrics are not specified, getDisplayMetrics returns null`() {
        val displayMetrics = ContextUtils.getDisplayMetrics(mock(), logger)
        assertNull(displayMetrics)
    }

    @Test
    fun `when Build MODEL specified, getFamily returns correct value`() {
        ShadowBuild.setModel("Pixel 3XL")
        val family = ContextUtils.getFamily(logger)
        assertEquals("Pixel", family)
    }

    @Test
    fun `when Build MODEL is not specified, getFamily returns null`() {
        ShadowBuild.setModel(null)
        val family = ContextUtils.getFamily(logger)
        assertNull(family)
    }

    @Test
    fun `when supported abis is specified, getArchitectures returns correct values`() {
        val architectures = ContextUtils.getArchitectures(BuildInfoProvider(logger))
        assertEquals("armeabi-v7a", architectures[0])
    }

    @Test
    fun `when memory info is specified, returns correct values`() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadowActivityManager = Shadow.extract<ShadowActivityManager>(activityManager)

        shadowActivityManager.setMemoryInfo(
            MemoryInfo().apply {
                availMem = 128
                totalMem = 2048
                lowMemory = true
            }
        )
        val memInfo = ContextUtils.getMemInfo(context, logger)
        assertEquals(128, memInfo!!.availMem)
        assertEquals(2048, memInfo.totalMem)
        assertTrue(memInfo.lowMemory)
    }

    @Test
    fun `when memory info is not specified, returns null`() {
        val memInfo = ContextUtils.getMemInfo(mock(), logger)
        assertNull(memInfo)
    }

    @Test
    fun `registerReceiver calls context_registerReceiver without exported flag on API 32-`() {
        val buildInfo = mock<BuildInfoProvider>()
        val receiver = mock<BroadcastReceiver>()
        val filter = mock<IntentFilter>()
        val context = mock<Context>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.S)
        ContextUtils.registerReceiver(context, buildInfo, receiver, filter)
        verify(context).registerReceiver(eq(receiver), eq(filter))
    }

    @Test
    fun `registerReceiver calls context_registerReceiver with exported flag on API 33+`() {
        val buildInfo = mock<BuildInfoProvider>()
        val receiver = mock<BroadcastReceiver>()
        val filter = mock<IntentFilter>()
        val context = mock<Context>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.TIRAMISU)
        ContextUtils.registerReceiver(context, buildInfo, receiver, filter)
        verify(context).registerReceiver(eq(receiver), eq(filter), eq(Context.RECEIVER_EXPORTED))
    }

    @Test
    fun `returns true when app started with foreground importance`() {
        assertTrue(ContextUtils.isForegroundImportance())
    }

    @Test
    fun `returns false when app started with importance different than foreground`() {
        shadowActivityManager.setProcesses(
            listOf(
                RunningAppProcessInfo().apply {
                    processName = "io.sentry.android.core.test"
                    pid = Process.myPid()
                    importance = RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
                }
            )
        )
        assertFalse(ContextUtils.isForegroundImportance())
    }
}
