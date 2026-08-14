package io.sentry.android.timber

import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.timber.BuildConfig.VERSION_NAME
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.LazyEvaluator.Evaluator
import java.io.Closeable
import timber.log.Timber

/** Sentry integration for Timber. */
public class SentryTimberIntegration(
  public val minEventLevel: SentryLevel = SentryLevel.ERROR,
  public val minBreadcrumbLevel: SentryLevel = SentryLevel.INFO,
  public val minLogsLevel: SentryLogLevel = SentryLogLevel.INFO,
) : Integration, Closeable {
  public val logsEnabled: Boolean
    get() = logsEnabledProvider.evaluate()

  private var logsEnabledProvider: Evaluator<Boolean> = Evaluator { false }

  public constructor(logsEnabled: Boolean) : this() {
    logsEnabledProvider = Evaluator { logsEnabled }
  }

  public constructor(
    minEventLevel: SentryLevel,
    minBreadcrumbLevel: SentryLevel,
    minLogsLevel: SentryLogLevel,
    logsEnabled: Boolean,
  ) : this(minEventLevel, minBreadcrumbLevel, minLogsLevel) {
    logsEnabledProvider = Evaluator { logsEnabled }
  }

  public constructor(logsEnabledProvider: Evaluator<Boolean>) : this() {
    this.logsEnabledProvider = logsEnabledProvider
  }

  private lateinit var tree: SentryTimberTree
  private lateinit var logger: ILogger

  private companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-timber", VERSION_NAME)
    }
  }

  override fun register(scopes: IScopes, options: SentryOptions) {
    logger = options.logger

    tree =
      SentryTimberTree(
        scopes,
        minEventLevel,
        minBreadcrumbLevel,
        minLogsLevel,
        logsEnabledProvider.evaluate(),
      )
    Timber.plant(tree)

    logger.log(SentryLevel.DEBUG, "SentryTimberIntegration installed.")
    addIntegrationToSdkVersion("Timber")
  }

  override fun close() {
    if (this::tree.isInitialized) {
      Timber.uproot(tree)

      if (this::logger.isInitialized) {
        logger.log(SentryLevel.DEBUG, "SentryTimberIntegration removed.")
      }
    }
  }
}
