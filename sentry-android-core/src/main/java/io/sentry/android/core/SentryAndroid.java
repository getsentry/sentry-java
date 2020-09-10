package io.sentry.android.core;

import android.content.Context;
import io.sentry.ILogger;
import io.sentry.OptionsContainer;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;

/** Sentry initialization class */
public final class SentryAndroid {

  private SentryAndroid() {}

  /**
   * Sentry initialization method if auto-init is disabled
   *
   * @param context Application. context
   */
  public static void init(@NotNull final Context context) {
    init(context, new AndroidLogger());
  }

  /**
   * Sentry initialization with a custom logger
   *
   * @param context Application. context
   * @param logger your custom logger that implements ILogger
   */
  public static void init(@NotNull final Context context, @NotNull ILogger logger) {
    init(context, logger, options -> {});
  }

  /**
   * Sentry initialization with a configuration handler that may override the default options
   *
   * @param context Application. context
   * @param configuration Sentry.OptionsConfiguration configuration handler
   */
  public static void init(
      @NotNull final Context context,
      @NotNull Sentry.OptionsConfiguration<SentryAndroidOptions> configuration) {
    init(context, new AndroidLogger(), configuration);
  }

  /**
   * Sentry initialization with a configuration handler and custom logger
   *
   * @param context Application. context
   * @param logger your custom logger that implements ILogger
   * @param configuration Sentry.OptionsConfiguration configuration handler
   */
  public static void init(
      @NotNull final Context context,
      @NotNull ILogger logger,
      @NotNull Sentry.OptionsConfiguration<SentryAndroidOptions> configuration) {
    try {
      Sentry.init(
          OptionsContainer.create(SentryAndroidOptions.class),
          options -> {
            AndroidOptionsInitializer.init(options, context, logger);
            configuration.configure(options);
          },
          true);
    } catch (IllegalAccessException e) {
      logger.log(SentryLevel.FATAL, "Fatal error during SentryAndroid.init(...)", e);

      // This is awful. Should we have this all on the interface and let the caller deal with these?
      // They mean bug in the SDK.
      throw new RuntimeException("Failed to initialize Sentry's SDK", e);
    } catch (InstantiationException e) {
      logger.log(SentryLevel.FATAL, "Fatal error during SentryAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize Sentry's SDK", e);
    } catch (NoSuchMethodException e) {
      logger.log(SentryLevel.FATAL, "Fatal error during SentryAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize Sentry's SDK", e);
    } catch (InvocationTargetException e) {
      logger.log(SentryLevel.FATAL, "Fatal error during SentryAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize Sentry's SDK", e);
    }
  }
}
