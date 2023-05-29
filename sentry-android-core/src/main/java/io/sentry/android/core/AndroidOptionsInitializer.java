package io.sentry.android.core;

import static io.sentry.android.core.NdkIntegration.SENTRY_NDK_CLASS_NAME;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.Build;
import io.sentry.DefaultTransactionPerformanceCollector;
import io.sentry.ILogger;
import io.sentry.SendFireAndForgetEnvelopeSender;
import io.sentry.SendFireAndForgetOutboxSender;
import io.sentry.SentryLevel;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.gestures.AndroidViewGestureTargetLocator;
import io.sentry.android.core.internal.modules.AssetsModulesLoader;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import io.sentry.cache.PersistingOptionsObserver;
import io.sentry.cache.PersistingScopeObserver;
import io.sentry.compose.gestures.ComposeGestureTargetLocator;
import io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Android Options initializer, it reads configurations from AndroidManifest and sets to the
 * SentryAndroidOptions. It also adds default values for some fields.
 */
@SuppressWarnings("Convert2MethodRef") // older AGP versions do not support method references
final class AndroidOptionsInitializer {

  static final String SENTRY_COMPOSE_GESTURE_INTEGRATION_CLASS_NAME =
      "io.sentry.compose.gestures.ComposeGestureTargetLocator";

  static final String SENTRY_COMPOSE_VIEW_HIERARCHY_INTEGRATION_CLASS_NAME =
      "io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter";

  static final String COMPOSE_CLASS_NAME = "androidx.compose.ui.node.Owner";

  /** private ctor */
  private AndroidOptionsInitializer() {}

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryAndroidOptions
   * @param context the Application context
   */
  @TestOnly
  static void loadDefaultAndMetadataOptions(
      final @NotNull SentryAndroidOptions options, final @NotNull Context context) {
    final ILogger logger = new AndroidLogger();
    loadDefaultAndMetadataOptions(options, context, logger, new BuildInfoProvider(logger));
  }

  /**
   * Init method of the Android Options initializer
   *
   * @param options the SentryAndroidOptions
   * @param context the Application context
   * @param logger the ILogger interface
   * @param buildInfoProvider the BuildInfoProvider interface
   */
  static void loadDefaultAndMetadataOptions(
      final @NotNull SentryAndroidOptions options,
      @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    Objects.requireNonNull(context, "The context is required.");

    // it returns null if ContextImpl, so let's check for nullability
    if (context.getApplicationContext() != null) {
      context = context.getApplicationContext();
    }

    Objects.requireNonNull(options, "The options object is required.");
    Objects.requireNonNull(logger, "The ILogger object is required.");

    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(logger);

    options.setDateProvider(new SentryAndroidDateProvider());

    ManifestMetadataReader.applyMetadata(context, options, buildInfoProvider);
    initializeCacheDirs(context, options);

    readDefaultOptionValues(options, context, buildInfoProvider);
  }

  @TestOnly
  static void initializeIntegrationsAndProcessors(
      final @NotNull SentryAndroidOptions options, final @NotNull Context context) {
    initializeIntegrationsAndProcessors(
        options,
        context,
        new BuildInfoProvider(new AndroidLogger()),
        new LoadClass(),
        false,
        false);
  }

  static void initializeIntegrationsAndProcessors(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass,
      final boolean isFragmentAvailable,
      final boolean isTimberAvailable) {

    if (options.getCacheDirPath() != null
        && options.getEnvelopeDiskCache() instanceof NoOpEnvelopeCache) {
      options.setEnvelopeDiskCache(new AndroidEnvelopeCache(options));
    }

    final ActivityFramesTracker activityFramesTracker =
        new ActivityFramesTracker(loadClass, options);

    installDefaultIntegrations(
        context,
        options,
        buildInfoProvider,
        loadClass,
        activityFramesTracker,
        isFragmentAvailable,
        isTimberAvailable);

    options.addEventProcessor(
        new DefaultAndroidEventProcessor(context, buildInfoProvider, options));
    options.addEventProcessor(new PerformanceAndroidEventProcessor(options, activityFramesTracker));
    options.addEventProcessor(new ScreenshotEventProcessor(options, buildInfoProvider));
    options.addEventProcessor(new ViewHierarchyEventProcessor(options));
    options.addEventProcessor(new AnrV2EventProcessor(context, options, buildInfoProvider));
    options.setTransportGate(new AndroidTransportGate(context, options.getLogger()));
    final SentryFrameMetricsCollector frameMetricsCollector =
        new SentryFrameMetricsCollector(context, options, buildInfoProvider);
    options.setTransactionProfiler(
        new AndroidTransactionProfiler(context, options, buildInfoProvider, frameMetricsCollector));
    options.setModulesLoader(new AssetsModulesLoader(context, options.getLogger()));

    final boolean isAndroidXScrollViewAvailable =
        loadClass.isClassAvailable("androidx.core.view.ScrollingView", options);
    final boolean isComposeUpstreamAvailable =
        loadClass.isClassAvailable(COMPOSE_CLASS_NAME, options);

    if (options.getGestureTargetLocators().isEmpty()) {
      final List<GestureTargetLocator> gestureTargetLocators = new ArrayList<>(2);
      gestureTargetLocators.add(new AndroidViewGestureTargetLocator(isAndroidXScrollViewAvailable));

      final boolean isComposeAvailable =
          (isComposeUpstreamAvailable
              && loadClass.isClassAvailable(
                  SENTRY_COMPOSE_GESTURE_INTEGRATION_CLASS_NAME, options));

      if (isComposeAvailable) {
        gestureTargetLocators.add(new ComposeGestureTargetLocator(options.getLogger()));
      }
      options.setGestureTargetLocators(gestureTargetLocators);
    }

    if (options.getViewHierarchyExporters().isEmpty()
        && isComposeUpstreamAvailable
        && loadClass.isClassAvailable(
            SENTRY_COMPOSE_VIEW_HIERARCHY_INTEGRATION_CLASS_NAME, options)) {

      final List<ViewHierarchyExporter> viewHierarchyExporters = new ArrayList<>(1);
      viewHierarchyExporters.add(new ComposeViewHierarchyExporter(options.getLogger()));
      options.setViewHierarchyExporters(viewHierarchyExporters);
    }

    options.setMainThreadChecker(AndroidMainThreadChecker.getInstance());
    if (options.getCollectors().isEmpty()) {
      options.addCollector(new AndroidMemoryCollector());
      options.addCollector(new AndroidCpuCollector(options.getLogger(), buildInfoProvider));
    }
    options.setTransactionPerformanceCollector(new DefaultTransactionPerformanceCollector(options));

    if (options.getCacheDirPath() != null) {
      options.addScopeObserver(new PersistingScopeObserver(options));
      options.addOptionsObserver(new PersistingOptionsObserver(options));
    }
  }

  private static void installDefaultIntegrations(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass,
      final @NotNull ActivityFramesTracker activityFramesTracker,
      final boolean isFragmentAvailable,
      final boolean isTimberAvailable) {

    // read the startup crash marker here to avoid doing double-IO for the SendCachedEnvelope
    // integrations below
    final boolean hasStartupCrashMarker = AndroidEnvelopeCache.hasStartupCrashMarker(options);

    options.addIntegration(
        new SendCachedEnvelopeIntegration(
            new SendFireAndForgetEnvelopeSender(() -> options.getCacheDirPath()),
            hasStartupCrashMarker));

    // Integrations are registered in the same order. NDK before adding Watch outbox,
    // because sentry-native move files around and we don't want to watch that.
    final Class<?> sentryNdkClass =
        isNdkAvailable(buildInfoProvider)
            ? loadClass.loadClass(SENTRY_NDK_CLASS_NAME, options.getLogger())
            : null;
    options.addIntegration(new NdkIntegration(sentryNdkClass));

    // this integration uses android.os.FileObserver, we can't move to sentry
    // before creating a pure java impl.
    options.addIntegration(EnvelopeFileObserverIntegration.getOutboxFileObserver());

    // Send cached envelopes from outbox path
    // this should be executed after NdkIntegration because sentry-native move files on init.
    // and we'd like to send them right away
    options.addIntegration(
        new SendCachedEnvelopeIntegration(
            new SendFireAndForgetOutboxSender(() -> options.getOutboxPath()),
            hasStartupCrashMarker));

    // AppLifecycleIntegration has to be installed before AnrIntegration, because AnrIntegration
    // relies on AppState set by it
    options.addIntegration(new AppLifecycleIntegration());
    options.addIntegration(AnrIntegrationFactory.create(context, buildInfoProvider));

    // registerActivityLifecycleCallbacks is only available if Context is an AppContext
    if (context instanceof Application) {
      options.addIntegration(
          new ActivityLifecycleIntegration(
              (Application) context, buildInfoProvider, activityFramesTracker));
      options.addIntegration(new CurrentActivityIntegration((Application) context));
      options.addIntegration(new UserInteractionIntegration((Application) context, loadClass));
      if (isFragmentAvailable) {
        options.addIntegration(new FragmentLifecycleIntegration((Application) context, true, true));
      }
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "ActivityLifecycle, FragmentLifecycle and UserInteraction Integrations need an Application class to be installed.");
    }

    if (isTimberAvailable) {
      options.addIntegration(new SentryTimberIntegration());
    }
    options.addIntegration(new AppComponentsBreadcrumbsIntegration(context));
    options.addIntegration(new SystemEventsBreadcrumbsIntegration(context));
    options.addIntegration(
        new NetworkBreadcrumbsIntegration(context, buildInfoProvider, options.getLogger()));
    options.addIntegration(new TempSensorBreadcrumbsIntegration(context));
    options.addIntegration(new PhoneStateBreadcrumbsIntegration(context));
  }

  /**
   * Reads and sets default option values that are Android specific like release and inApp
   *
   * @param options the SentryAndroidOptions
   * @param context the Android context methods
   */
  private static void readDefaultOptionValues(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    final PackageInfo packageInfo =
        ContextUtils.getPackageInfo(context, options.getLogger(), buildInfoProvider);
    if (packageInfo != null) {
      // Sets App's release if not set by Manifest
      if (options.getRelease() == null) {
        options.setRelease(
            getSentryReleaseVersion(
                packageInfo, ContextUtils.getVersionCode(packageInfo, buildInfoProvider)));
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

    final @Nullable Properties debugMetaProperties =
        loadDebugMetaProperties(context, options.getLogger());

    if (debugMetaProperties != null) {
      if (options.getProguardUuid() == null) {
        final @Nullable String proguardUuid =
            debugMetaProperties.getProperty("io.sentry.ProguardUuids");
        options.getLogger().log(SentryLevel.DEBUG, "Proguard UUID found: %s", proguardUuid);
        options.setProguardUuid(proguardUuid);
      }

      if (options.getBundleIds().isEmpty()) {
        final @Nullable String bundleIdStrings =
            debugMetaProperties.getProperty("io.sentry.bundle-ids");
        options.getLogger().log(SentryLevel.DEBUG, "Bundle IDs found: %s", bundleIdStrings);
        if (bundleIdStrings != null) {
          final @NotNull String[] bundleIds = bundleIdStrings.split(",", -1);
          for (final String bundleId : bundleIds) {
            options.addBundleId(bundleId);
          }
        }
      }
    }
  }

  private static @Nullable Properties loadDebugMetaProperties(
      final @NotNull Context context, final @NotNull ILogger logger) {
    final AssetManager assets = context.getAssets();
    // one may have thousands of asset files and looking up this list might slow down the SDK init.
    // quite a bit, for this reason, we try to open the file directly and take care of errors
    // like FileNotFoundException
    try (final InputStream is =
        new BufferedInputStream(assets.open("sentry-debug-meta.properties"))) {
      final Properties properties = new Properties();
      properties.load(is);
      return properties;
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
   * @param options the SentryAndroidOptions
   */
  private static void initializeCacheDirs(
      final @NotNull Context context, final @NotNull SentryAndroidOptions options) {
    final File cacheDir = new File(context.getCacheDir(), "sentry");
    options.setCacheDirPath(cacheDir.getAbsolutePath());
  }

  private static boolean isNdkAvailable(final @NotNull BuildInfoProvider buildInfoProvider) {
    return buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN;
  }
}
