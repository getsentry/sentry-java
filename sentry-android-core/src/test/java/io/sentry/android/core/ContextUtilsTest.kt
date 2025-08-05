package io.sentry.android.core

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.NoOpLogger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
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

@Config(sdk = [33])
@RunWith(AndroidJUnit4::class)
class ContextUtilsTest {
  private lateinit var shadowActivityManager: ShadowActivityManager
  private lateinit var context: Context
  private lateinit var logger: ILogger

  @BeforeTest
  fun `set up`() {
    ContextUtils.resetInstance()
    context = ApplicationProvider.getApplicationContext()
    logger = NoOpLogger.getInstance()
    ShadowBuild.reset()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    shadowActivityManager = Shadow.extract(activityManager)
  }

  @Test
  fun `Given a valid context, returns a valid PackageInfo`() {
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
    val versionCode = ContextUtils.getVersionCode(packageInfo!!, mock())

    assertNotNull(versionCode)
  }

  @Test
  fun `Given a valid PackageInfo, returns a valid versionName`() {
    // VersionName is null during tests, so we mock it the second time
    val packageInfo = ContextUtils.getPackageInfo(context, mock())!!
    val versionName = ContextUtils.getVersionName(packageInfo)
    assertNull(versionName)
    val mockedPackageInfo = spy(packageInfo) { it.versionName = "" }
    val mockedVersionName = ContextUtils.getVersionName(mockedPackageInfo)
    assertNotNull(mockedVersionName)
  }

  @Test
  fun `when context is valid, getApplicationName returns application name`() {
    val appName = ContextUtils.getApplicationName(context)
    assertEquals("io.sentry.android.core.test", appName)
  }

  @Test
  fun `when context is invalid, getApplicationName returns null`() {
    val appName = ContextUtils.getApplicationName(mock())
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
    val mockedContext =
      spy(context) {
        val mockedPackageManager =
          spy(mock.packageManager) {
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
  fun `given a valid PackageInfo, returns valid splitNames`() {
    val splitNames = arrayOf<String?>("config.arm64_v8a")
    val mockedContext = mock<Context>()
    val mockedPackageManager = mock<PackageManager>()
    val mockedApplicationInfo = mock<ApplicationInfo>()
    val mockedPackageInfo = mock<PackageInfo>()
    mockedPackageInfo.splitNames = splitNames

    whenever(mockedContext.packageName).thenReturn("dummy")

    whenever(
        mockedPackageManager.getApplicationInfo(
          any<String>(),
          any<PackageManager.ApplicationInfoFlags>(),
        )
      )
      .thenReturn(mockedApplicationInfo)

    whenever(
        mockedPackageManager.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>())
      )
      .thenReturn(mockedPackageInfo)

    whenever(mockedContext.packageManager).thenReturn(mockedPackageManager)

    val splitApksInfo = ContextUtils.retrieveSplitApksInfo(mockedContext, BuildInfoProvider(logger))
    assertContentEquals(splitNames, splitApksInfo!!.splitNames)
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
    val architectures = ContextUtils.getArchitectures()
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

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  @Test
  fun `registerReceiver calls context_registerReceiver without exported flag on API 32-`() {
    val buildInfo = mock<BuildInfoProvider>()
    val receiver = mock<BroadcastReceiver>()
    val filter = mock<IntentFilter>()
    val context = mock<Context>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.S)
    ContextUtils.registerReceiver(context, buildInfo, receiver, filter, null)
    verify(context).registerReceiver(eq(receiver), eq(filter))
  }

  @Test
  fun `registerReceiver calls context_registerReceiver with exported flag on API 33+`() {
    val buildInfo = mock<BuildInfoProvider>()
    val receiver = mock<BroadcastReceiver>()
    val filter = mock<IntentFilter>()
    val context = mock<Context>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.TIRAMISU)
    ContextUtils.registerReceiver(context, buildInfo, receiver, filter, null)
    verify(context).registerReceiver(eq(receiver), eq(filter), eq(Context.RECEIVER_NOT_EXPORTED))
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

  @Test
  fun `getApplicationContext returns context if app context is null`() {
    val contextMock = mock<Context>()
    val appContext = ContextUtils.getApplicationContext(contextMock)
    assertSame(contextMock, appContext)
  }

  @Test
  fun `getApplicationContext returns app context`() {
    val contextMock = mock<Context>()
    val appContextMock = mock<Context>()
    whenever(contextMock.applicationContext).thenReturn(appContextMock)

    val appContext = ContextUtils.getApplicationContext(contextMock)
    assertSame(appContextMock, appContext)
  }

  @Test
  fun `appIsLibraryForComposePreview is correctly determined`() {
    fun getMockContext(packageName: String, activityClassName: String): Context {
      val context = mock<Context>()
      val activityManager = mock<ActivityManager>()
      whenever(context.packageName).thenReturn(packageName)
      whenever(context.getSystemService(eq(Context.ACTIVITY_SERVICE))).thenReturn(activityManager)
      val taskInfo = ActivityManager.RecentTaskInfo()
      taskInfo.baseIntent =
        Intent().setComponent(ComponentName("com.example.library", activityClassName))
      val appTask = mock<ActivityManager.AppTask>()
      whenever(appTask.taskInfo).thenReturn(taskInfo)
      whenever(activityManager.appTasks).thenReturn(listOf(appTask))

      return context
    }

    assertTrue(
      ContextUtils.appIsLibraryForComposePreview(
        getMockContext("com.example.library.test", "androidx.compose.ui.tooling.PreviewActivity")
      )
    )
    assertFalse(
      ContextUtils.appIsLibraryForComposePreview(
        getMockContext("com.example.library.test", "com.example.HomeActivity")
      )
    )
    assertFalse(
      ContextUtils.appIsLibraryForComposePreview(
        getMockContext("com.example.library", "androidx.compose.ui.tooling.PreviewActivity")
      )
    )
  }
}
