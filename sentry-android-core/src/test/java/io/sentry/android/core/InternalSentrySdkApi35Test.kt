package io.sentry.android.core

import android.app.Application
import android.app.ApplicationStartInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.performance.AppStartMetrics
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
  sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM],
  shadows = [SentryShadowActivityManager::class],
)
class InternalSentrySdkApi35Test {
  @BeforeTest
  fun setup() {
    AppStartMetrics.getInstance().clear()
    SentryShadowActivityManager.reset()
  }

  @Test
  fun `app start measurement includes app start reason for hybrids`() {
    val mockStartInfo = mock<ApplicationStartInfo>()
    whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_STARTED)
    whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
    whenever(mockStartInfo.reason).thenReturn(ApplicationStartInfo.START_REASON_LAUNCHER)
    SentryShadowActivityManager.setHistoricalProcessStartReasons(listOf(mockStartInfo))

    val app = ApplicationProvider.getApplicationContext<Application>()
    AppStartMetrics.getInstance().registerLifecycleCallbacks(app)

    assertEquals("launcher", InternalSentrySdk.getAppStartMeasurement()["reason"])
  }
}
