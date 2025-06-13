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
import timber.log.Timber
import java.io.Closeable

/**
 * Sentry integration for Timber.
 */
public class SentryTimberIntegration(
    public val minEventLevel: SentryLevel = SentryLevel.ERROR,
    public val minBreadcrumbLevel: SentryLevel = SentryLevel.INFO,
    public val minLogsLevel: SentryLogLevel = SentryLogLevel.INFO
) : Integration, Closeable {
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

        tree = SentryTimberTree(scopes, minEventLevel, minBreadcrumbLevel, minLogsLevel)
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
