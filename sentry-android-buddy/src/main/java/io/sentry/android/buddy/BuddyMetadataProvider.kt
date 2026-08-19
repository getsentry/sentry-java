package io.sentry.android.buddy

import android.app.Application
import android.os.Build
import io.sentry.android.buddy.model.BuddyAppInfo
import io.sentry.android.buddy.model.BuddyDeviceInfo

internal interface BuddyMetadataProvider {
  fun appInfo(): BuddyAppInfo

  fun deviceInfo(): BuddyDeviceInfo
}

internal class AndroidBuddyMetadataProvider(
  private val application: Application,
  private val sentryFacade: BuddySentryFacade,
) : BuddyMetadataProvider {
  override fun appInfo(): BuddyAppInfo {
    val packageInfo = packageInfo()
    return BuddyAppInfo(
      packageName = application.packageName,
      versionName = packageInfo?.versionName,
      versionCode =
        packageInfo?.let { info ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
          } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
          }
        },
      release = sentryFacade.release,
      environment = sentryFacade.environment,
    )
  }

  override fun deviceInfo(): BuddyDeviceInfo {
    return BuddyDeviceInfo(
      manufacturer = Build.MANUFACTURER,
      model = Build.MODEL,
      osVersion = Build.VERSION.RELEASE,
    )
  }

  @Suppress("DEPRECATION")
  private fun packageInfo(): android.content.pm.PackageInfo? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        application.packageManager.getPackageInfo(
          application.packageName,
          android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        application.packageManager.getPackageInfo(application.packageName, 0)
      }
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
      null
    }
  }
}
