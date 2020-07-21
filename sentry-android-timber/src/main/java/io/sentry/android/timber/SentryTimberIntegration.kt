package io.sentry.android.timber

import io.sentry.core.IHub
import io.sentry.core.ILogger
import io.sentry.core.Integration
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.protocol.SdkVersion
import java.io.Closeable
import timber.log.Timber

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
        addPackage(options.sdkVersion)
        logger = options.logger

        tree = SentryTimberTree(hub, minEventLevel, minBreadcrumbLevel)
        Timber.plant(tree)

        logger.log(SentryLevel.DEBUG, "SentryTimberIntegration installed.")
    }

    override fun close() {
        if (this::tree.isInitialized) {
            Timber.uproot(tree)

            if (this::logger.isInitialized) {
                logger.log(SentryLevel.DEBUG, "SentryTimberIntegration removed.")
            }
        }
    }

    private fun addPackage(sdkVersion: SdkVersion?) {
        sdkVersion?.addPackage("maven:sentry-android-timber", BuildConfig.VERSION_NAME)
    }
}
