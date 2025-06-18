package io.sentry.android.core

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.internal.util.Permissions
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PermissionsTest {
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `isConnected won't throw exception`() {
        assertNotNull(Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE))
    }
}
