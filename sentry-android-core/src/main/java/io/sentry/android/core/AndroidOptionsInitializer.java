package io.sentry.android.core;

import static io.sentry.android.core.NdkIntegration.SENTRY_NDK_CLASS_NAME;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.Build;
import io.sentry.ILogger;
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration;
import io.sentry.SendFireAndForgetEnvelopeSender;
import io.sentry.SendFireAndForgetOutboxSender;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android Options initializer, it reads configurations from AndroidManifest and sets to the
 * SentryOptions. It also adds default values for some fields.
 */
@SuppressWarnings("Convert2MethodRef") // older AGP versions do not support method references
final class AndroidOptionsInitializer {

  static final String SENTRY_FRAGMENT_INTEGRATION_CLASS_NAME =
      "io.sentry.android.fragment.FragmentLifecycleIntegration";

  static final String SENTRY_TIMBER_INTEGRATION_CLASS_NAME =
      "io.sentry.android.timber.SentryTimberIntegration";

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
      @NotNull Context context,
      final @NotNull ILogger logger) {
    init(options, context, logger, new BuildInfoProvider());
  }

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryOptions
   * @param context the Application context
   * @param logger the ILogger interface
   * @param buildInfoProvider the IBuildInfoProvider interface
   */
  static void init(
      final @NotNull SentryAndroidOptions options,
      @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull IBuildInfoProvider buildInfoProvider) {
    init(options, context, logger, buildInfoProvider, new LoadClass());
  }

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryOptions
   * @param context the Application context
   * @param logger the ILogger interface
   * @param buildInfoProvider the IBuildInfoProvider interface
   * @param loadClass the LoadClass wrapper
   */
  static void init(
      final @NotNull SentryAndroidOptions options,
      @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull IBuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass) {
    Objects.requireNonNull(context, "The context is required.");

    // it returns null if ContextImpl, so let's check for nullability
    if (context.getApplicationContext() != null) {
      context = context.getApplicationContext();
    }

    Objects.requireNonNull(options, "The options object is required.");
    Objects.requireNonNull(logger, "The ILogger object is required.");

    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(logger);

    ManifestMetadataReader.applyMetadata(context, options);
    initializeCacheDirs(context, options);

    final ActivityFramesTracker activityFramesTracker = new ActivityFramesTracker(loadClass);
    installDefaultIntegrations(
        context, options, buildInfoProvider, loadClass, activityFramesTracker);

    readDefaultOptionValues(options, context);

    options.addEventProcessor(new DefaultAndroidEventProcessor(context, logger, buildInfoProvider));
    options.addEventProcessor(new PerformanceAndroidEventProcessor(options, activityFramesTracker));

    options.setTransportGate(new AndroidTransportGate(context, options.getLogger()));
  }

  private static void installDefaultIntegrations(
      final @NotNull Context context,
      final @NotNull SentryOptions options,
      final @NotNull IBuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass,
      final @NotNull ActivityFramesTracker activityFramesTracker) {

    options.addIntegration(
        new SendCachedEnvelopeFireAndForgetIntegration(
            new SendFireAndForgetEnvelopeSender(() -> options.getCacheDirPath())));

    // Integrations are registered in the same order. NDK before adding Watch outbox,
    // because sentry-native move files around and we don't want to watch that.
    final Class<?> sentryNdkClass = loadNdkIfAvailable(options, buildInfoProvider, loadClass);
    options.addIntegration(new NdkIntegration(sentryNdkClass));

    // this integration uses android.os.FileObserver, we can't move to sentry
    // before creating a pure java impl.
    options.addIntegration(EnvelopeFileObserverIntegration.getOutboxFileObserver());

    // Send cached envelopes from outbox path
    // this should be executed after NdkIntegration because sentry-native move files on init.
    // and we'd like to send them right away
    options.addIntegration(
        new SendCachedEnvelopeFireAndForgetIntegration(
            new SendFireAndForgetOutboxSender(() -> options.getOutboxPath())));

    options.addIntegration(new AnrIntegration(context));
    options.addIntegration(new AppLifecycleIntegration());

    // registerActivityLifecycleCallbacks is only available if Context is an AppContext
    if (context instanceof Application) {
      options.addIntegration(
          new ActivityLifecycleIntegration(
              (Application) context, buildInfoProvider, activityFramesTracker));
      options.addIntegration(new UserInteractionIntegration((Application) context, loadClass));
      if (isIntegrationAvailable(SENTRY_FRAGMENT_INTEGRATION_CLASS_NAME, options, loadClass)) {
        options.addIntegration(new FragmentLifecycleIntegration((Application) context));
      }
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "ActivityLifecycle, FragmentLifecycle and UserInteraction Integrations need an Application class to be installed.");
    }
    if (isIntegrationAvailable(SENTRY_TIMBER_INTEGRATION_CLASS_NAME, options, loadClass)) {
      options.addIntegration(new SentryTimberIntegration());
    }
    options.addIntegration(new AppComponentsBreadcrumbsIntegration(context));
    options.addIntegration(new SystemEventsBreadcrumbsIntegration(context));
    options.addIntegration(new TempSensorBreadcrumbsIntegration(context));
    options.addIntegration(new PhoneStateBreadcrumbsIntegration(context));
  }

  /**
   * Reads and sets default option values that are Android specific like release and inApp
   *
   * @param options the SentryOptions
   * @param context the Android context methods
   */
  private static void readDefaultOptionValues(
      final @NotNull SentryAndroidOptions options, final @NotNull Context context) {
    final PackageInfo packageInfo = ContextUtils.getPackageInfo(context, options.getLogger());
    if (packageInfo != null) {
      // Sets App's release if not set by Manifest
      if (options.getRelease() == null) {
        options.setRelease(
            getSentryReleaseVersion(packageInfo, ContextUtils.getVersionCode(packageInfo)));
      }

      // Sets the App's package name as InApp
      final String packageName = packageInfo.packageName;
      if (packageName != null && !packageName.startsWith("android.")) {
        options.addInAppInclude(packageName);
      }
    }

    if (options.getDistinctId() == null) {
      try {
        options.setDistinctId(Installation.id(context));
      } catch (RuntimeException e) {
        options.getLogger().log(SentryLevel.ERROR, "Could not generate distinct Id.", e);
      }
    }

    if (options.getProguardUuid() == null) {
      options.setProguardUuid(getProguardUUID(context, options.getLogger()));
    }
  }

  private static @Nullable String getProguardUUID(
      final @NotNull Context context, final @NotNull ILogger logger) {
    final AssetManager assets = context.getAssets();
    // one may have thousands of asset files and looking up this list might slow down the SDK init.
    // quite a bit, for this reason, we try to open the file directly and take care of errors
    // like FileNotFoundException
    try (final InputStream is =
        new BufferedInputStream(assets.open("sentry-debug-meta.properties"))) {
      final Properties properties = new Properties();
      properties.load(is);

      final String uuid = properties.getProperty("io.sentry.ProguardUuids");
      logger.log(SentryLevel.DEBUG, "Proguard UUID found: %s", uuid);
      return uuid;
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.INFO, "sentry-debug-meta.properties file was not found.");
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error getting Proguard UUIDs.", e);
    } catch (RuntimeException e) {
      logger.log(SentryLevel.ERROR, "sentry-debug-meta.properties file is malformed.", e);
    }

    return null;
  }

  /**
   * Returns the sentry release version (eg io.sentry.sample@1.0.0+10000) -
   * packageName@versionName+buildVersion
   *
   * @param packageInfo the PackageInfo
   * @param versionCode the versionCode
   * @return the sentry release version as a String
   */
  private static @NotNull String getSentryReleaseVersion(
      final @NotNull PackageInfo packageInfo, final @NotNull String versionCode) {
    return packageInfo.packageName + "@" + packageInfo.versionName + "+" + versionCode;
  }

  /**
   * Sets the cache dirs like sentry, outbox and sessions
   *
   * @param context the Application context
   * @param options the SentryOptions
   */
  private static void initializeCacheDirs(
      final @NotNull Context context, final @NotNull SentryOptions options) {
    final File cacheDir = new File(context.getCacheDir(), "sentry");
    options.setCacheDirPath(cacheDir.getAbsolutePath());
  }

  private static boolean isNdkAvailable(final @NotNull IBuildInfoProvider buildInfoProvider) {
    return buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN;
  }

  private static @Nullable Class<?> loadNdkIfAvailable(
      final @NotNull SentryOptions options,
      final @NotNull IBuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass) {
    if (isNdkAvailable(buildInfoProvider)) {
      try {
        return loadClass.loadClass(SENTRY_NDK_CLASS_NAME);
      } catch (ClassNotFoundException e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to load SentryNdk.", e);
      } catch (UnsatisfiedLinkError e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to load (UnsatisfiedLinkError) SentryNdk.", e);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to initialize SentryNdk.", e);
      }
    }
    return null;
  }

  private static boolean isIntegrationAvailable(
      final @NotNull String integrationClassName,
      final @NotNull SentryOptions options,
      final @NotNull LoadClass loadClass) {
    boolean isAvailable;
    try {
      loadClass.loadClass(integrationClassName);
      isAvailable = true;
    } catch (ClassNotFoundException ignored) {
      isAvailable = false;
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              integrationClassName + " won't be installed as it's not available on the classpath");
    }
    return isAvailable;
  }
}
