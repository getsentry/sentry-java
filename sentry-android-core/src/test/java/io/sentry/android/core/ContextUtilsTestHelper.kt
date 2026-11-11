package io.sentry.android.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import java.io.FileNotFoundException
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object ContextUtilsTestHelper {
  fun mockMetaData(
    mockContext: Context = createMockContext(hasAppContext = false),
    metaData: Bundle,
    assets: AssetManager? = null,
  ): Context {
    val mockPackageManager = mock<PackageManager>()
    val mockApplicationInfo = mock<ApplicationInfo>()

    whenever(mockContext.packageName).thenReturn("io.sentry.sample.test")
    whenever(mockContext.packageManager).thenReturn(mockPackageManager)
    whenever(
        mockPackageManager.getApplicationInfo(mockContext.packageName, PackageManager.GET_META_DATA)
      )
      .thenReturn(mockApplicationInfo)

    val contextAssets = assets ?: assetsWithoutModulesFile()
    whenever(mockContext.assets).thenReturn(contextAssets)

    mockApplicationInfo.metaData = metaData
    return mockContext
  }

  fun createMockContext(hasAppContext: Boolean = true): Context {
    // A real Context always has assets. Without sentry-external-modules.txt, open() throws.
    val assets = assetsWithoutModulesFile()
    val mockApp = mock<Context>()
    whenever(mockApp.assets).thenReturn(assets)
    if (hasAppContext) {
      val application = mock<Application>()
      whenever(application.assets).thenReturn(assets)
      whenever(mockApp.applicationContext).thenReturn(application)
    } else {
      whenever(mockApp.applicationContext).thenReturn(null)
    }
    return mockApp
  }

  private fun assetsWithoutModulesFile(): AssetManager {
    val mockAssets = mock<AssetManager>()
    whenever(mockAssets.open(any())).thenThrow(FileNotFoundException())
    return mockAssets
  }
}
