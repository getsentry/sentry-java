package io.sentry.android.core;

import android.content.Context;
import io.sentry.core.EnvelopeReader;
import io.sentry.core.IEnvelopeReader;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Android Options initializer, it reads configurations from AndroidManifest and sets to the
 * SentryOptions. It also adds default values for some fields.
 */
final class AndroidOptionsInitializer {

  /** private ctor */
  private AndroidOptionsInitializer() {}

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryOptions
   * @param context the Application context
   */
  static void init(final @NotNull SentryAndroidOptions options, final @NotNull Context context) {
    Objects.requireNonNull(context, "The application context is required.");
    Objects.requireNonNull(options, "The options object is required.");

    init(options, context, new AndroidLogger());
  }

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryOptions
   * @param context the Application context
   * @param logger the ILogger interface
   */
  static void init(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Context context,
      final @NotNull ILogger logger) {
    Objects.requireNonNull(context, "The application context is required.");
    Objects.requireNonNull(options, "The options object is required.");
    Objects.requireNonNull(logger, "The ILogger object is required.");

    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(logger);

    options.setSentryClientName(BuildConfig.SENTRY_CLIENT_NAME + "/" + BuildConfig.VERSION_NAME);

    ManifestMetadataReader.applyMetadata(context, options);
    initializeCacheDirs(context, options);
    setDefaultInApp(context, options);

    final IEnvelopeReader envelopeReader = new EnvelopeReader();
    // Integrations are registered in the same order. Watch outbox before adding NDK:
    options.addIntegration(EnvelopeFileObserverIntegration.getOutboxFileObserver(envelopeReader));
    options.addIntegration(new NdkIntegration());
    options.addIntegration(new AnrIntegration());
    options.addIntegration(new SessionTrackingIntegration());

    options.addEventProcessor(new DefaultAndroidEventProcessor(context, options));
    options.setSerializer(new AndroidSerializer(options.getLogger(), envelopeReader));

    options.setTransportGate(new AndroidTransportGate(context, options.getLogger()));
  }

  /**
   * Sets the App's package name as InApp
   *
   * @param context the Application context
   * @param options the SentryOptions
   */
  private static void setDefaultInApp(
      final @NotNull Context context, final @NotNull SentryOptions options) {
    final String packageName = context.getPackageName();
    if (packageName != null && !packageName.startsWith("android.")) {
      options.addInAppInclude(packageName);
    }
  }

  /**
   * It creates the cache dirs like sentry, outbox and sessions
   *
   * @param context the Application context
   * @param options the SentryOptions
   */
  private static void initializeCacheDirs(
      final @NotNull Context context, final @NotNull SentryOptions options) {
    final File cacheDir = new File(context.getCacheDir(), "sentry");
    cacheDir.mkdirs();
    options.setCacheDirPath(cacheDir.getAbsolutePath());

    if (options.getOutboxPath() != null) {
      new File(options.getOutboxPath()).mkdirs();
    } else {
      options.getLogger().log(SentryLevel.WARNING, "No outbox dir path is defined in options.");
    }
    if (options.getSessionsPath() != null) {
      new File(options.getSessionsPath()).mkdirs();
    } else {
      options.getLogger().log(SentryLevel.WARNING, "No session dir path is defined in options.");
    }
  }
}
