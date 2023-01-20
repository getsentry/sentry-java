package io.sentry.android.timber

import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.Integration
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.timber.BuildConfig.VERSION_NAME
import timber.log.Timber
import java.io.Closeable

/**
 * Sentry integration for Timber.
 */
class SentryTimberIntegration(
    val minEventLevel: SentryLevel = SentryLevel.ERROR,
    val minBreadcrumbLevel: SentryLevel = SentryLevel.INFO
) : Integration, Closeable {
    private lateinit var tree: SentryTimberTree
    private lateinit var logger: ILogger

    override fun register(hub: IHub, options: SentryOptions) {
        options.sdkVersion?.addPackage("maven:io.sentry:sentry-android-timber", VERSION_NAME)

        logger = options.logger

        tree = SentryTimberTree(hub, minEventLevel, minBreadcrumbLevel)
        Timber.plant(tree)

        logger.log(SentryLevel.DEBUG, "SentryTimberIntegration installed.")
        options.sdkVersion?.addIntegration("Timber")
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
