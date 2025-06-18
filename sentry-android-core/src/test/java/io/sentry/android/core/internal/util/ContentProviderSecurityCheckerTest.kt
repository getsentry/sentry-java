package io.sentry.android.core.internal.util

import android.content.ContentProvider
import android.content.Context
import android.os.Build
import io.sentry.android.core.BuildInfoProvider
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ContentProviderSecurityCheckerTest {
    private class Fixture {
        val buildInfoProvider = mock<BuildInfoProvider>()

        fun getSut(sdkVersion: Int = Build.VERSION_CODES.O): ContentProviderSecurityChecker {
            whenever(buildInfoProvider.sdkInfoVersion).thenReturn(sdkVersion)

            return ContentProviderSecurityChecker(
                buildInfoProvider,
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When sdk version is less than vulnerable versions, security check is not performed`() {
        val contentProvider = mock<ContentProvider>()

        fixture.getSut(Build.VERSION_CODES.N_MR1).checkPrivilegeEscalation(contentProvider)

        verifyNoInteractions(contentProvider)
    }

    @Test
    fun `When sdk version is greater than vulnerable versions, security check is not performed`() {
        val contentProvider = mock<ContentProvider>()

        fixture.getSut(Build.VERSION_CODES.Q).checkPrivilegeEscalation(contentProvider)

        verifyNoInteractions(contentProvider)
    }

    @Test
    fun `When calling package is null, security check exception is thrown`() {
        val contentProvider = mock<ContentProvider>()

        contentProvider.mockPackages(null)

        assertFailsWith<SecurityException> {
            fixture.getSut().checkPrivilegeEscalation(contentProvider)
        }
    }

    @Test
    fun `When calling package does not match app package, security check exception is thrown`() {
        val contentProvider = mock<ContentProvider>()

        contentProvider.mockPackages("{$APP_PACKAGE}.attacker")

        assertFailsWith<SecurityException> {
            fixture.getSut().checkPrivilegeEscalation(contentProvider)
        }
    }

    @Test
    fun `When calling package matches app package, no security exception thrown`() {
        val contentProvider = mock<ContentProvider>()

        contentProvider.mockPackages(APP_PACKAGE)

        fixture.getSut().checkPrivilegeEscalation(contentProvider)

        // No exception!
    }
}

private fun ContentProvider.mockPackages(callingPackage: String?) {
    whenever(this.callingPackage).thenReturn(callingPackage)

    val context = mock<Context>()
    whenever(this.context).thenReturn(context)
    whenever(context.packageName).thenReturn(APP_PACKAGE)
}

private const val APP_PACKAGE = "com.app"
