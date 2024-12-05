package io.sentry.android.core;

import static io.sentry.android.core.NdkIntegration.SENTRY_NDK_CLASS_NAME;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import io.sentry.DeduplicateMultithreadedEventProcessor;
import io.sentry.DefaultTransactionPerformanceCollector;
import io.sentry.ILogger;
import io.sentry.ITransactionProfiler;
import io.sentry.NoOpConnectionStatusProvider;
import io.sentry.SendFireAndForgetEnvelopeSender;
import io.sentry.SendFireAndForgetOutboxSender;
import io.sentry.SentryLevel;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.debugmeta.AssetsDebugMetaLoader;
import io.sentry.android.core.internal.gestures.AndroidViewGestureTargetLocator;
import io.sentry.android.core.internal.modules.AssetsModulesLoader;
import io.sentry.android.core.internal.util.AndroidConnectionStatusProvider;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.replay.DefaultReplayBreadcrumbConverter;
import io.sentry.android.replay.ReplayIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import io.sentry.cache.PersistingOptionsObserver;
import io.sentry.cache.PersistingScopeObserver;
import io.sentry.compose.gestures.ComposeGestureTargetLocator;
import io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Android Options initializer, it reads configurations from AndroidManifest and sets to the
 * SentryAndroidOptions. It also adds default values for some fields.
 */
@SuppressWarnings("Convert2MethodRef") // older AGP versions do not support method references
final class AndroidOptionsInitializer {

  static final long DEFAULT_FLUSH_TIMEOUT_MS = 4000;

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

    context = ContextUtils.getApplicationContext(context);

    Objects.requireNonNull(options, "The options object is required.");
    Objects.requireNonNull(logger, "The ILogger object is required.");

    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(logger);

    options.setDateProvider(new SentryAndroidDateProvider());

    // set a lower flush timeout on Android to avoid ANRs
    options.setFlushTimeoutMillis(DEFAULT_FLUSH_TIMEOUT_MS);

    options.setFrameMetricsCollector(
        new SentryFrameMetricsCollector(context, logger, buildInfoProvider));

    ManifestMetadataReader.applyMetadata(context, options, buildInfoProvider);
    options.setCacheDirPath(getCacheDir(context).getAbsolutePath());

    readDefaultOptionValues(options, context, buildInfoProvider);
  }

  @TestOnly
  static void initializeIntegrationsAndProcessors(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Context context,
      final @NotNull LoadClass loadClass,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    initializeIntegrationsAndProcessors(
        options,
        context,
        new BuildInfoProvider(new AndroidLogger()),
        loadClass,
        activityFramesTracker);
  }

  static void initializeIntegrationsAndProcessors(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass,
      final @NotNull ActivityFramesTracker activityFramesTracker) {

    if (options.getCacheDirPath() != null
        && options.getEnvelopeDiskCache() instanceof NoOpEnvelopeCache) {
      options.setEnvelopeDiskCache(new AndroidEnvelopeCache(options));
    }

    if (options.getConnectionStatusProvider() instanceof NoOpConnectionStatusProvider) {
      options.setConnectionStatusProvider(
          new AndroidConnectionStatusProvider(context, options.getLogger(), buildInfoProvider));
    }

    options.addEventProcessor(new DeduplicateMultithreadedEventProcessor(options));
    options.addEventProcessor(
        new DefaultAndroidEventProcessor(context, buildInfoProvider, options));
    options.addEventProcessor(new PerformanceAndroidEventProcessor(options, activityFramesTracker));
    options.addEventProcessor(new ScreenshotEventProcessor(options, buildInfoProvider));
    options.addEventProcessor(new ViewHierarchyEventProcessor(options));
    options.addEventProcessor(new AnrV2EventProcessor(context, options, buildInfoProvider));
    options.setTransportGate(new AndroidTransportGate(options));

    // Check if the profiler was already instantiated in the app start.
    // We use the Android profiler, that uses a global start/stop api, so we need to preserve the
    // state of the profiler, and it's only possible retaining the instance.
    synchronized (AppStartMetrics.getInstance()) {
      final @Nullable ITransactionProfiler appStartProfiler =
          AppStartMetrics.getInstance().getAppStartProfiler();
      if (appStartProfiler != null) {
        options.setTransactionProfiler(appStartProfiler);
        AppStartMetrics.getInstance().setAppStartProfiler(null);
      } else {
        options.setTransactionProfiler(
            new AndroidTransactionProfiler(
                context,
                options,
                buildInfoProvider,
                Objects.requireNonNull(
                    options.getFrameMetricsCollector(),
                    "options.getFrameMetricsCollector is required")));
      }
    }
    options.setModulesLoader(new AssetsModulesLoader(context, options.getLogger()));
    options.setDebugMetaLoader(new AssetsDebugMetaLoader(context, options.getLogger()));

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
    if (options.getPerformanceCollectors().isEmpty()) {
      options.addPerformanceCollector(new AndroidMemoryCollector());
      options.addPerformanceCollector(
          new AndroidCpuCollector(options.getLogger(), buildInfoProvider));

      if (options.isEnablePerformanceV2()) {
        options.addPerformanceCollector(
            new SpanFrameMetricsCollector(
                options,
                Objects.requireNonNull(
                    options.getFrameMetricsCollector(),
                    "options.getFrameMetricsCollector is required")));
      }
    }
    options.setTransactionPerformanceCollector(new DefaultTransactionPerformanceCollector(options));

    if (options.getCacheDirPath() != null) {
      if (options.isEnableScopePersistence()) {
        options.addScopeObserver(new PersistingScopeObserver(options));
      }
      options.addOptionsObserver(new PersistingOptionsObserver(options));
    }
  }

  static void installDefaultIntegrations(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull LoadClass loadClass,
      final @NotNull ActivityFramesTracker activityFramesTracker,
      final boolean isFragmentAvailable,
      final boolean isTimberAvailable,
      final boolean isReplayAvailable) {

    // Integration MUST NOT cache option values in ctor, as they will be configured later by the
    // user

    // read the startup crash marker here to avoid doing double-IO for the SendCachedEnvelope
    // integrations below
    LazyEvaluator<Boolean> startupCrashMarkerEvaluator =
        new LazyEvaluator<>(() -> AndroidEnvelopeCache.hasStartupCrashMarker(options));

    options.addIntegration(
        new SendCachedEnvelopeIntegration(
            new SendFireAndForgetEnvelopeSender(() -> options.getCacheDirPath()),
            startupCrashMarkerEvaluator));

    // Integrations are registered in the same order. NDK before adding Watch outbox,
    // because sentry-native move files around and we don't want to watch that.
    final Class<?> sentryNdkClass = loadClass.loadClass(SENTRY_NDK_CLASS_NAME, options.getLogger());
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
            startupCrashMarkerEvaluator));

    // AppLifecycleIntegration has to be installed before AnrIntegration, because AnrIntegration
    // relies on AppState set by it
    options.addIntegration(new AppLifecycleIntegration());
    options.addIntegration(AnrIntegrationFactory.create(context, buildInfoProvider));

    // registerActivityLifecycleCallbacks is only available if Context is an AppContext
    if (context instanceof Application) {
      options.addIntegration(
          new ActivityLifecycleIntegration(
              (Application) context, buildInfoProvider, activityFramesTracker));
      options.addIntegration(new ActivityBreadcrumbsIntegration((Application) context));
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
    if (isReplayAvailable) {
      final ReplayIntegration replay =
          new ReplayIntegration(context, CurrentDateProvider.getInstance());
      replay.setBreadcrumbConverter(new DefaultReplayBreadcrumbConverter());
      options.addIntegration(replay);
      options.setReplayController(replay);
    }
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
   * Retrieve the Sentry cache dir.
   *
   * @param context the Application context
   */
  static @NotNull File getCacheDir(final @NotNull Context context) {
    return new File(context.getCacheDir(), "sentry");
  }
}
