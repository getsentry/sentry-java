package io.sentry.android.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.io.File

object ContextUtils {
    fun mockMetaData(mockContext: Context = createMockContext(), metaData: Bundle): Context {
        val mockPackageManager: PackageManager = mock()
        val mockApplicationInfo: ApplicationInfo = mock()

        whenever(mockContext.packageName).thenReturn("io.sentry.sample.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getApplicationInfo(mockContext.packageName, PackageManager.GET_META_DATA))
            .thenReturn(mockApplicationInfo)

        mockApplicationInfo.metaData = metaData
        return mockContext
    }

    fun createMockContext(): Context {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.cacheDir).thenReturn(File(""))
        return mockContext
    }
}
