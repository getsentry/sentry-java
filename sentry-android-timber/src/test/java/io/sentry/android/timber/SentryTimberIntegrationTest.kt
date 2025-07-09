package io.sentry.android.timber

import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SdkVersion
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import timber.log.Timber

class SentryTimberIntegrationTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val options = SentryOptions().apply { sdkVersion = SdkVersion("test", "1.2.3") }

    fun getSut(
      minEventLevel: SentryLevel = SentryLevel.ERROR,
      minBreadcrumbLevel: SentryLevel = SentryLevel.INFO,
      minLogsLevel: SentryLogLevel = SentryLogLevel.INFO,
    ): SentryTimberIntegration =
      SentryTimberIntegration(
        minEventLevel = minEventLevel,
        minBreadcrumbLevel = minBreadcrumbLevel,
        minLogLevel = minLogsLevel,
      )
  }

  private val fixture = Fixture()

  @BeforeTest
  fun beforeTest() {
    Timber.uprootAll()
  }

  @Test
  fun `Integrations plants a tree into Timber on register`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertEquals(1, Timber.treeCount())

    val trees = Timber.forest()
    val first = trees.first()
    assertTrue(first is SentryTimberTree)
  }

  @Test
  fun `Integrations plants the SentryTimberTree tree`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    Timber.e(Throwable())
    verify(fixture.scopes).captureEvent(any())
  }

  @Test
  fun `Integrations removes a tree from Timber on close integration`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertEquals(1, Timber.treeCount())

    sut.close()
    assertEquals(0, Timber.treeCount())
  }

  @Test
  fun `Integrations do not throw if close is called before register`() {
    val sut = fixture.getSut()
    sut.close()

    assertEquals(0, Timber.treeCount())
  }

  @Test
  fun `Integrations pass the right min levels`() {
    val sut =
      fixture.getSut(
        minEventLevel = SentryLevel.INFO,
        minBreadcrumbLevel = SentryLevel.DEBUG,
        minLogsLevel = SentryLogLevel.TRACE,
      )
    sut.register(fixture.scopes, fixture.options)

    assertEquals(sut.minEventLevel, SentryLevel.INFO)
    assertEquals(sut.minBreadcrumbLevel, SentryLevel.DEBUG)
    assertEquals(sut.minLogLevel, SentryLogLevel.TRACE)
  }

  @Test
  fun `Integration adds itself to the package list`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertTrue(
      fixture.options.sdkVersion!!.packageSet.any {
        it.name == "maven:io.sentry:sentry-android-timber" && it.version == BuildConfig.VERSION_NAME
      }
    )
  }

  @Test
  fun `Integration adds itself to the integration list`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertTrue(fixture.options.sdkVersion!!.integrationSet.contains("Timber"))
  }
}
