package io.sentry.android.core

import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.test.callMethod
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.use
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any

@RunWith(AndroidJUnit4::class)
class SentryInitProviderTest {
  private var sentryInitProvider = SentryInitProvider()

  @BeforeTest
  fun `set up`() {
    Sentry.close()
    ContextUtils.resetInstance()
  }

  @Test
  fun `when missing applicationId, SentryInitProvider throws`() {
    val providerInfo = ProviderInfo()

    val mockContext = ContextUtilsTestHelper.createMockContext()
    providerInfo.authority = SentryInitProvider::class.java.name
    assertFailsWith<IllegalStateException> {
      sentryInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data, SDK initializes`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")

    sentryInitProvider.attachInfo(mockContext, providerInfo)

    assertTrue(Sentry.isEnabled())
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is empty, SDK doesnt initialize`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "")

    sentryInitProvider.attachInfo(mockContext, providerInfo)

    assertFalse(Sentry.isEnabled())
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is not set or null, SDK throws exception`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, null)

    assertFailsWith<IllegalArgumentException> {
      sentryInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is invalid, SDK should throw an error`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "invalid dsn")

    assertFailsWith<IllegalArgumentException> {
      sentryInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, auto-init in meta-data is set to false, SDK doesnt initialize`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

    sentryInitProvider.attachInfo(mockContext, providerInfo)

    assertFalse(Sentry.isEnabled())
  }

  @Test
  fun `when App context is null, do nothing`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    sentryInitProvider.callMethod(
      "attachInfo",
      parameterTypes = arrayOf(Context::class.java, ProviderInfo::class.java),
      null,
      providerInfo,
    )

    assertFalse(Sentry.isEnabled())
  }

  @Test
  fun `when applicationId is defined, ndk in meta-data is set to false, NDK doesnt initialize`() {
    val sentryOptions = SentryAndroidOptions()

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)
    metaData.putBoolean(ManifestMetadataReader.NDK_ENABLE, false)

    AndroidOptionsInitializer.loadDefaultAndMetadataOptions(sentryOptions, mockContext)

    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, sentryOptions)
    AndroidOptionsInitializer.installDefaultIntegrations(
      mockContext,
      sentryOptions,
      BuildInfoProvider(AndroidLogger()),
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
      sentryOptions,
      mockContext,
      loadClass,
      activityFramesTracker,
      false,
    )

    assertFalse(sentryOptions.isEnableNdk)
  }

  @Test
  fun `skips init in compose preview mode`() {
    val providerInfo = ProviderInfo()

    assertFalse(Sentry.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    Mockito.mockStatic(ContextUtils::class.java).use { contextUtils ->
      contextUtils
        .`when`<Boolean> { ContextUtils.appIsLibraryForComposePreview(any()) }
        .thenReturn(true)
      sentryInitProvider.attachInfo(mockContext, providerInfo)
    }
    assertFalse(Sentry.isEnabled())
  }

  companion object {
    private const val AUTHORITY = "io.sentry.sample.SentryInitProvider"
  }
}
