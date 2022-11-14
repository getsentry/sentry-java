package io.sentry.android.core;

import android.content.Context;
import android.os.SystemClock;
import io.sentry.DateUtils;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.Integration;
import io.sentry.OptionsContainer;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import io.sentry.transport.NoOpEnvelopeCache;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Sentry initialization class */
public final class SentryAndroid {

  // static to rely on Class load init.
  private static final @NotNull Date appStartTime = DateUtils.getCurrentDateTime();
  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long appStart = SystemClock.uptimeMillis();

  static final String SENTRY_FRAGMENT_INTEGRATION_CLASS_NAME =
      "io.sentry.android.fragment.FragmentLifecycleIntegration";

  static final String SENTRY_TIMBER_INTEGRATION_CLASS_NAME =
      "io.sentry.android.timber.SentryTimberIntegration";

  private static final String TIMBER_CLASS_NAME = "timber.log.Timber";
  private static final String FRAGMENT_CLASS_NAME =
      "androidx.fragment.app.FragmentManager$FragmentLifecycleCallbacks";

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
  public static synchronized void init(
      @NotNull final Context context,
      @NotNull ILogger logger,
      @NotNull Sentry.OptionsConfiguration<SentryAndroidOptions> configuration) {
    // if SentryPerformanceProvider was disabled or removed, we set the App Start when
    // the SDK is called.
    AppStartState.getInstance().setAppStartTime(appStart, appStartTime);

    try {
      Sentry.init(
          OptionsContainer.create(SentryAndroidOptions.class),
          options -> {
            final LoadClass classLoader = new LoadClass();
            final boolean isTimberUpstreamAvailable =
                classLoader.isClassAvailable(TIMBER_CLASS_NAME, options);
            final boolean isFragmentUpstreamAvailable =
                classLoader.isClassAvailable(FRAGMENT_CLASS_NAME, options);
            final boolean isFragmentAvailable =
                (isFragmentUpstreamAvailable
                    && classLoader.isClassAvailable(
                        SENTRY_FRAGMENT_INTEGRATION_CLASS_NAME, options));
            final boolean isTimberAvailable =
                (isTimberUpstreamAvailable
                    && classLoader.isClassAvailable(SENTRY_TIMBER_INTEGRATION_CLASS_NAME, options));

            AndroidOptionsInitializer.init(
                options, context, logger, isFragmentAvailable, isTimberAvailable);
            configuration.configure(options);
            deduplicateIntegrations(options, isFragmentAvailable, isTimberAvailable);
            resetEnvelopeCacheIfNeeded(options);
          },
          true);

      final @NotNull IHub hub = Sentry.getCurrentHub();
      if (hub.getOptions().isEnableAutoSessionTracking()) {
        hub.startSession();
      }
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

  /**
   * Deduplicate potentially duplicated Fragment and Timber integrations, which can be added
   * automatically by our SDK as well as by the user. The user's ones win over ours.
   *
   * @param options SentryOptions to retrieve integrations from
   */
  private static void deduplicateIntegrations(
      final @NotNull SentryOptions options,
      final boolean isFragmentAvailable,
      final boolean isTimberAvailable) {

    final List<Integration> timberIntegrations = new ArrayList<>();
    final List<Integration> fragmentIntegrations = new ArrayList<>();

    for (final Integration integration : options.getIntegrations()) {
      if (isFragmentAvailable) {
        if (integration instanceof FragmentLifecycleIntegration) {
          fragmentIntegrations.add(integration);
        }
      }
      if (isTimberAvailable) {
        if (integration instanceof SentryTimberIntegration) {
          timberIntegrations.add(integration);
        }
      }
    }

    if (fragmentIntegrations.size() > 1) {
      for (int i = 0; i < fragmentIntegrations.size() - 1; i++) {
        final Integration integration = fragmentIntegrations.get(i);
        options.getIntegrations().remove(integration);
      }
    }

    if (timberIntegrations.size() > 1) {
      for (int i = 0; i < timberIntegrations.size() - 1; i++) {
        final Integration integration = timberIntegrations.get(i);
        options.getIntegrations().remove(integration);
      }
    }
  }

  /**
   * Resets envelope cache if {@link SentryOptions#getCacheDirPath()} was set to null by the user
   * and the IEnvelopCache implementation remained ours (AndroidEnvelopeCache), which relies on
   * cacheDirPath set.
   *
   * @param options SentryOptions to retrieve cacheDirPath from
   */
  private static void resetEnvelopeCacheIfNeeded(final @NotNull SentryAndroidOptions options) {
    if (options.getCacheDirPath() == null
        && options.getEnvelopeDiskCache() instanceof AndroidEnvelopeCache) {
      options.setEnvelopeDiskCache(NoOpEnvelopeCache.getInstance());
    }
  }
}
