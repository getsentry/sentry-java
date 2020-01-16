package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import io.sentry.android.core.util.ConnectivityChecker
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityCheckerTest {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `isConnected won't throw exception`() {
        ConnectivityChecker.isConnected(context, mock())
    }

    @Test
    fun `getConnectionType won't throw exception`() {
        ConnectivityChecker.getConnectionType(context, mock())
    }
}
