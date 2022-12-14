package io.sentry.android.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException

object ContextUtilsTest {
    fun mockMetaData(mockContext: Context = createMockContext(hasAppContext = false), metaData: Bundle): Context {
        val mockPackageManager = mock<PackageManager>()
        val mockApplicationInfo = mock<ApplicationInfo>()
        val assets = mock<AssetManager>()

        whenever(mockContext.packageName).thenReturn("io.sentry.sample.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getApplicationInfo(mockContext.packageName, PackageManager.GET_META_DATA))
            .thenReturn(mockApplicationInfo)
        whenever(assets.open(any())).thenThrow(FileNotFoundException())
        whenever(mockContext.assets).thenReturn(assets)

        mockApplicationInfo.metaData = metaData
        return mockContext
    }

    fun createMockContext(hasAppContext: Boolean = true): Context {
        val mockApp = mock<Context>()
        whenever(mockApp.applicationContext).thenReturn(if (hasAppContext) mock<Application>() else null)
        return mockApp
    }
}
