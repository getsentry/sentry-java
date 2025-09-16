package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.OptionsContainer;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.Session;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import io.sentry.util.AutoClosableReentrantLock;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry initialization class */
public final class SentryAndroid {

  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long sdkInitMillis = SystemClock.uptimeMillis();

  static final String SENTRY_FRAGMENT_INTEGRATION_CLASS_NAME =
      "io.sentry.android.fragment.FragmentLifecycleIntegration";

  static final String SENTRY_TIMBER_INTEGRATION_CLASS_NAME =
      "io.sentry.android.timber.SentryTimberIntegration";

  static final String SENTRY_REPLAY_INTEGRATION_CLASS_NAME =
      "io.sentry.android.replay.ReplayIntegration";

  static final String SENTRY_DISTRIBUTION_INTEGRATION_CLASS_NAME =
      "io.sentry.android.distribution.DistributionIntegration";

  private static final String TIMBER_CLASS_NAME = "timber.log.Timber";
  private static final String FRAGMENT_CLASS_NAME =
      "androidx.fragment.app.FragmentManager$FragmentLifecycleCallbacks";

  protected static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

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
  @SuppressLint("NewApi")
  public static void init(
      @NotNull final Context context,
      @NotNull ILogger logger,
      @NotNull Sentry.OptionsConfiguration<SentryAndroidOptions> configuration) {
    try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
      Sentry.init(
          OptionsContainer.create(SentryAndroidOptions.class),
          options -> {
            final io.sentry.util.LoadClass classLoader = new io.sentry.util.LoadClass();
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
            final boolean isReplayAvailable =
                classLoader.isClassAvailable(SENTRY_REPLAY_INTEGRATION_CLASS_NAME, options);
            final boolean isDistributionAvailable =
                classLoader.isClassAvailable(SENTRY_DISTRIBUTION_INTEGRATION_CLASS_NAME, options);

            final BuildInfoProvider buildInfoProvider = new BuildInfoProvider(logger);
            final io.sentry.util.LoadClass loadClass = new io.sentry.util.LoadClass();
            final ActivityFramesTracker activityFramesTracker =
                new ActivityFramesTracker(loadClass, options);

            AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
                options, context, logger, buildInfoProvider);

            // We install the default integrations before the option configuration, so that the user
            // can remove any of them. Integrations will not evaluate the options immediately, but
            // will use them later, after being configured.
            AndroidOptionsInitializer.installDefaultIntegrations(
                context,
                options,
                buildInfoProvider,
                loadClass,
                activityFramesTracker,
                isFragmentAvailable,
                isTimberAvailable,
                isReplayAvailable,
                isDistributionAvailable);

            try {
              configuration.configure(options);
            } catch (Throwable t) {
              // let it slip, but log it
              options
                  .getLogger()
                  .log(
                      SentryLevel.ERROR,
                      "Error in the 'OptionsConfiguration.configure' callback.",
                      t);
            }

            // if SentryPerformanceProvider was disabled or removed,
            // we set the app start / sdk init time here instead
            final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
            if (options.isEnablePerformanceV2()
                && buildInfoProvider.getSdkInfoVersion() >= android.os.Build.VERSION_CODES.N) {
              final @NotNull TimeSpan appStartTimeSpan = appStartMetrics.getAppStartTimeSpan();
              if (appStartTimeSpan.hasNotStarted()) {
                appStartTimeSpan.setStartedAt(Process.getStartUptimeMillis());
              }
            }
            if (context.getApplicationContext() instanceof Application) {
              appStartMetrics.registerLifecycleCallbacks(
                  (Application) context.getApplicationContext());
            }
            final @NotNull TimeSpan sdkInitTimeSpan = appStartMetrics.getSdkInitTimeSpan();
            if (sdkInitTimeSpan.hasNotStarted()) {
              sdkInitTimeSpan.setStartedAt(sdkInitMillis);
            }

            AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
                options, context, buildInfoProvider, loadClass, activityFramesTracker);

            deduplicateIntegrations(options, isFragmentAvailable, isTimberAvailable);
          },
          true);

      final @NotNull IScopes scopes = Sentry.getCurrentScopes();
      if (ContextUtils.isForegroundImportance()) {
        if (scopes.getOptions().isEnableAutoSessionTracking()) {
          // The LifecycleWatcher of AppLifecycleIntegration may already started a session
          // so only start a session if it's not already started
          // This e.g. happens on React Native, or e.g. on deferred SDK init
          final AtomicBoolean sessionStarted = new AtomicBoolean(false);
          scopes.configureScope(
              scope -> {
                final @Nullable Session currentSession = scope.getSession();
                if (currentSession != null && currentSession.getStarted() != null) {
                  sessionStarted.set(true);
                }
              });
          if (!sessionStarted.get()) {
            scopes.startSession();
          }
        }
        scopes.getOptions().getReplayController().start();
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
   * automatically by our SDK as well as by the user. The user's ones (provided last in the
   * options.integrations list) win over ours.
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
}
