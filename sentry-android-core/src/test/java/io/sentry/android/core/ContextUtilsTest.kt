package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ContextUtilsTest {

    private lateinit var shadowActivityManager: ShadowActivityManager
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        shadowActivityManager = Shadow.extract(activityManager)
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
