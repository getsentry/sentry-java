package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import io.sentry.ILogger;
import io.sentry.InitPriority;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.protocol.SdkVersion;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Class responsible for reading values from manifest and setting them to the options */
final class ManifestMetadataReader {

  static final String DSN = "io.sentry.dsn";
  static final String DEBUG = "io.sentry.debug";
  static final String DEBUG_LEVEL = "io.sentry.debug.level";
  static final String SAMPLE_RATE = "io.sentry.sample-rate";
  static final String ANR_ENABLE = "io.sentry.anr.enable";
  static final String ANR_REPORT_DEBUG = "io.sentry.anr.report-debug";
  static final String ANR_TIMEOUT_INTERVAL_MILLIS = "io.sentry.anr.timeout-interval-millis";
  static final String ANR_ATTACH_THREAD_DUMPS = "io.sentry.anr.attach-thread-dumps";

  static final String AUTO_INIT = "io.sentry.auto-init";
  static final String NDK_ENABLE = "io.sentry.ndk.enable";
  static final String NDK_SCOPE_SYNC_ENABLE = "io.sentry.ndk.scope-sync.enable";
  static final String RELEASE = "io.sentry.release";
  static final String ENVIRONMENT = "io.sentry.environment";
  static final String SDK_NAME = "io.sentry.sdk.name";
  static final String SDK_VERSION = "io.sentry.sdk.version";

  static final String AUTO_SESSION_TRACKING_ENABLE = "io.sentry.auto-session-tracking.enable";
  static final String SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS =
      "io.sentry.session-tracking.timeout-interval-millis";

  static final String BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE =
      "io.sentry.breadcrumbs.activity-lifecycle";
  static final String BREADCRUMBS_APP_LIFECYCLE_ENABLE = "io.sentry.breadcrumbs.app-lifecycle";
  static final String BREADCRUMBS_SYSTEM_EVENTS_ENABLE = "io.sentry.breadcrumbs.system-events";
  static final String BREADCRUMBS_NETWORK_EVENTS_ENABLE = "io.sentry.breadcrumbs.network-events";
  static final String BREADCRUMBS_APP_COMPONENTS_ENABLE = "io.sentry.breadcrumbs.app-components";
  static final String BREADCRUMBS_USER_INTERACTION_ENABLE =
      "io.sentry.breadcrumbs.user-interaction";

  static final String UNCAUGHT_EXCEPTION_HANDLER_ENABLE =
      "io.sentry.uncaught-exception-handler.enable";

  static final String TRACES_SAMPLE_RATE = "io.sentry.traces.sample-rate";
  static final String TRACES_ACTIVITY_ENABLE = "io.sentry.traces.activity.enable";
  static final String TRACES_ACTIVITY_AUTO_FINISH_ENABLE =
      "io.sentry.traces.activity.auto-finish.enable";
  static final String TRACES_UI_ENABLE = "io.sentry.traces.user-interaction.enable";

  static final String TTFD_ENABLE = "io.sentry.traces.time-to-full-display.enable";

  static final String PROFILES_SAMPLE_RATE = "io.sentry.traces.profiling.sample-rate";

  static final String PROFILE_SESSION_SAMPLE_RATE =
      "io.sentry.traces.profiling.session-sample-rate";

  @ApiStatus.Experimental static final String TRACE_SAMPLING = "io.sentry.traces.trace-sampling";
  static final String TRACE_PROPAGATION_TARGETS = "io.sentry.traces.trace-propagation-targets";

  static final String ATTACH_THREADS = "io.sentry.attach-threads";
  static final String PROGUARD_UUID = "io.sentry.proguard-uuid";
  static final String IDLE_TIMEOUT = "io.sentry.traces.idle-timeout";

  static final String ATTACH_SCREENSHOT = "io.sentry.attach-screenshot";
  static final String ATTACH_VIEW_HIERARCHY = "io.sentry.attach-view-hierarchy";
  static final String CLIENT_REPORTS_ENABLE = "io.sentry.send-client-reports";
  static final String COLLECT_ADDITIONAL_CONTEXT = "io.sentry.additional-context";

  static final String SEND_DEFAULT_PII = "io.sentry.send-default-pii";

  static final String PERFORM_FRAMES_TRACKING = "io.sentry.traces.frames-tracking";

  static final String SENTRY_GRADLE_PLUGIN_INTEGRATIONS = "io.sentry.gradle-plugin-integrations";

  static final String ENABLE_ROOT_CHECK = "io.sentry.enable-root-check";

  static final String ENABLE_SENTRY = "io.sentry.enabled";

  static final String SEND_MODULES = "io.sentry.send-modules";

  static final String ENABLE_PERFORMANCE_V2 = "io.sentry.performance-v2.enable";

  static final String ENABLE_APP_START_PROFILING = "io.sentry.profiling.enable-app-start";

  static final String ENABLE_SCOPE_PERSISTENCE = "io.sentry.enable-scope-persistence";

  static final String REPLAYS_SESSION_SAMPLE_RATE = "io.sentry.session-replay.session-sample-rate";

  static final String REPLAYS_ERROR_SAMPLE_RATE = "io.sentry.session-replay.on-error-sample-rate";

  static final String REPLAYS_MASK_ALL_TEXT = "io.sentry.session-replay.mask-all-text";

  static final String REPLAYS_MASK_ALL_IMAGES = "io.sentry.session-replay.mask-all-images";

  static final String FORCE_INIT = "io.sentry.force-init";

  static final String MAX_BREADCRUMBS = "io.sentry.max-breadcrumbs";

  /** ManifestMetadataReader ctor */
  private ManifestMetadataReader() {}

  /**
   * Reads configurations from Manifest and sets it to the options
   *
   * @param context the application context
   * @param options the SentryAndroidOptions
   */
  @SuppressWarnings("deprecation")
  static void applyMetadata(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    Objects.requireNonNull(context, "The application context is required.");
    Objects.requireNonNull(options, "The options object is required.");

    try {
      final Bundle metadata = getMetadata(context, options.getLogger(), buildInfoProvider);
      final ILogger logger = options.getLogger();

      if (metadata != null) {
        options.setDebug(readBool(metadata, logger, DEBUG, options.isDebug()));

        if (options.isDebug()) {
          final String level =
              readString(
                  metadata,
                  logger,
                  DEBUG_LEVEL,
                  options.getDiagnosticLevel().name().toLowerCase(Locale.ROOT));
          if (level != null) {
            options.setDiagnosticLevel(SentryLevel.valueOf(level.toUpperCase(Locale.ROOT)));
          }
        }

        options.setAnrEnabled(readBool(metadata, logger, ANR_ENABLE, options.isAnrEnabled()));

        // use enableAutoSessionTracking as fallback
        options.setEnableAutoSessionTracking(
            readBool(
                metadata,
                logger,
                AUTO_SESSION_TRACKING_ENABLE,
                options.isEnableAutoSessionTracking()));

        if (options.getSampleRate() == null) {
          final Double sampleRate = readDouble(metadata, logger, SAMPLE_RATE);
          if (sampleRate != -1) {
            options.setSampleRate(sampleRate);
          }
        }

        options.setAnrReportInDebug(
            readBool(metadata, logger, ANR_REPORT_DEBUG, options.isAnrReportInDebug()));

        options.setAnrTimeoutIntervalMillis(
            readLong(
                metadata,
                logger,
                ANR_TIMEOUT_INTERVAL_MILLIS,
                options.getAnrTimeoutIntervalMillis()));

        options.setAttachAnrThreadDump(
            readBool(metadata, logger, ANR_ATTACH_THREAD_DUMPS, options.isAttachAnrThreadDump()));

        final String dsn = readString(metadata, logger, DSN, options.getDsn());
        final boolean enabled = readBool(metadata, logger, ENABLE_SENTRY, options.isEnabled());

        if (!enabled || (dsn != null && dsn.isEmpty())) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Sentry enabled flag set to false or DSN is empty: disabling sentry-android");
        } else if (dsn == null) {
          options
              .getLogger()
              .log(SentryLevel.FATAL, "DSN is required. Use empty string to disable SDK.");
        }

        options.setEnabled(enabled);
        options.setDsn(dsn);

        options.setEnableNdk(readBool(metadata, logger, NDK_ENABLE, options.isEnableNdk()));

        options.setEnableScopeSync(
            readBool(metadata, logger, NDK_SCOPE_SYNC_ENABLE, options.isEnableScopeSync()));

        options.setRelease(readString(metadata, logger, RELEASE, options.getRelease()));

        options.setEnvironment(readString(metadata, logger, ENVIRONMENT, options.getEnvironment()));

        options.setSessionTrackingIntervalMillis(
            readLong(
                metadata,
                logger,
                SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS,
                options.getSessionTrackingIntervalMillis()));

        options.setMaxBreadcrumbs(
            (int) readLong(metadata, logger, MAX_BREADCRUMBS, options.getMaxBreadcrumbs()));

        options.setEnableActivityLifecycleBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE,
                options.isEnableActivityLifecycleBreadcrumbs()));

        options.setEnableAppLifecycleBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_APP_LIFECYCLE_ENABLE,
                options.isEnableAppLifecycleBreadcrumbs()));

        options.setEnableSystemEventBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_SYSTEM_EVENTS_ENABLE,
                options.isEnableSystemEventBreadcrumbs()));

        options.setEnableAppComponentBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_APP_COMPONENTS_ENABLE,
                options.isEnableAppComponentBreadcrumbs()));

        options.setEnableUserInteractionBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_USER_INTERACTION_ENABLE,
                options.isEnableUserInteractionBreadcrumbs()));

        options.setEnableNetworkEventBreadcrumbs(
            readBool(
                metadata,
                logger,
                BREADCRUMBS_NETWORK_EVENTS_ENABLE,
                options.isEnableNetworkEventBreadcrumbs()));

        options.setEnableUncaughtExceptionHandler(
            readBool(
                metadata,
                logger,
                UNCAUGHT_EXCEPTION_HANDLER_ENABLE,
                options.isEnableUncaughtExceptionHandler()));

        options.setAttachThreads(
            readBool(metadata, logger, ATTACH_THREADS, options.isAttachThreads()));

        options.setAttachScreenshot(
            readBool(metadata, logger, ATTACH_SCREENSHOT, options.isAttachScreenshot()));

        options.setAttachViewHierarchy(
            readBool(metadata, logger, ATTACH_VIEW_HIERARCHY, options.isAttachViewHierarchy()));

        options.setSendClientReports(
            readBool(metadata, logger, CLIENT_REPORTS_ENABLE, options.isSendClientReports()));

        final boolean isAutoInitEnabled = readBool(metadata, logger, AUTO_INIT, true);
        if (isAutoInitEnabled) {
          options.setInitPriority(InitPriority.LOW);
        }

        options.setForceInit(readBool(metadata, logger, FORCE_INIT, options.isForceInit()));

        options.setCollectAdditionalContext(
            readBool(
                metadata,
                logger,
                COLLECT_ADDITIONAL_CONTEXT,
                options.isCollectAdditionalContext()));

        if (options.getTracesSampleRate() == null) {
          final Double tracesSampleRate = readDouble(metadata, logger, TRACES_SAMPLE_RATE);
          if (tracesSampleRate != -1) {
            options.setTracesSampleRate(tracesSampleRate);
          }
        }

        options.setTraceSampling(
            readBool(metadata, logger, TRACE_SAMPLING, options.isTraceSampling()));

        options.setEnableAutoActivityLifecycleTracing(
            readBool(
                metadata,
                logger,
                TRACES_ACTIVITY_ENABLE,
                options.isEnableAutoActivityLifecycleTracing()));

        options.setEnableActivityLifecycleTracingAutoFinish(
            readBool(
                metadata,
                logger,
                TRACES_ACTIVITY_AUTO_FINISH_ENABLE,
                options.isEnableActivityLifecycleTracingAutoFinish()));

        if (options.getProfilesSampleRate() == null) {
          final Double profilesSampleRate = readDouble(metadata, logger, PROFILES_SAMPLE_RATE);
          if (profilesSampleRate != -1) {
            options.setProfilesSampleRate(profilesSampleRate);
          }
        }

        if (options.getProfileSessionSampleRate() == 0.0) {
          final double profileSessionSampleRate =
              readDouble(metadata, logger, PROFILE_SESSION_SAMPLE_RATE);
          if (profileSessionSampleRate != -1) {
            options.getExperimental().setProfileSessionSampleRate(profileSessionSampleRate);
          }
        }

        options.setEnableUserInteractionTracing(
            readBool(metadata, logger, TRACES_UI_ENABLE, options.isEnableUserInteractionTracing()));

        options.setEnableTimeToFullDisplayTracing(
            readBool(metadata, logger, TTFD_ENABLE, options.isEnableTimeToFullDisplayTracing()));

        final long idleTimeout = readLong(metadata, logger, IDLE_TIMEOUT, -1);
        if (idleTimeout != -1) {
          options.setIdleTimeout(idleTimeout);
        }

        @Nullable
        List<String> tracePropagationTargets =
            readList(metadata, logger, TRACE_PROPAGATION_TARGETS);

        if (metadata.containsKey(TRACE_PROPAGATION_TARGETS) && tracePropagationTargets == null) {
          options.setTracePropagationTargets(Collections.emptyList());
        } else if (tracePropagationTargets != null) {
          options.setTracePropagationTargets(tracePropagationTargets);
        }

        options.setEnableFramesTracking(readBool(metadata, logger, PERFORM_FRAMES_TRACKING, true));

        options.setProguardUuid(
            readString(metadata, logger, PROGUARD_UUID, options.getProguardUuid()));

        SdkVersion sdkInfo = options.getSdkVersion();
        if (sdkInfo == null) {
          // Is already set by the Options constructor, let's use an empty default otherwise.
          sdkInfo = new SdkVersion("", "");
        }
        sdkInfo.setName(readStringNotNull(metadata, logger, SDK_NAME, sdkInfo.getName()));
        sdkInfo.setVersion(readStringNotNull(metadata, logger, SDK_VERSION, sdkInfo.getVersion()));
        options.setSdkVersion(sdkInfo);

        options.setSendDefaultPii(
            readBool(metadata, logger, SEND_DEFAULT_PII, options.isSendDefaultPii()));

        // sdkInfo.addIntegration();

        List<String> integrationsFromGradlePlugin =
            readList(metadata, logger, SENTRY_GRADLE_PLUGIN_INTEGRATIONS);
        if (integrationsFromGradlePlugin != null) {
          for (String integration : integrationsFromGradlePlugin) {
            SentryIntegrationPackageStorage.getInstance().addIntegration(integration);
          }
        }

        options.setEnableRootCheck(
            readBool(metadata, logger, ENABLE_ROOT_CHECK, options.isEnableRootCheck()));

        options.setSendModules(readBool(metadata, logger, SEND_MODULES, options.isSendModules()));

        options.setEnablePerformanceV2(
            readBool(metadata, logger, ENABLE_PERFORMANCE_V2, options.isEnablePerformanceV2()));

        options.setEnableAppStartProfiling(
            readBool(
                metadata, logger, ENABLE_APP_START_PROFILING, options.isEnableAppStartProfiling()));

        options.setEnableScopePersistence(
            readBool(
                metadata, logger, ENABLE_SCOPE_PERSISTENCE, options.isEnableScopePersistence()));

        if (options.getExperimental().getSessionReplay().getSessionSampleRate() == null) {
          final Double sessionSampleRate =
              readDouble(metadata, logger, REPLAYS_SESSION_SAMPLE_RATE);
          if (sessionSampleRate != -1) {
            options.getExperimental().getSessionReplay().setSessionSampleRate(sessionSampleRate);
          }
        }

        if (options.getExperimental().getSessionReplay().getOnErrorSampleRate() == null) {
          final Double onErrorSampleRate = readDouble(metadata, logger, REPLAYS_ERROR_SAMPLE_RATE);
          if (onErrorSampleRate != -1) {
            options.getExperimental().getSessionReplay().setOnErrorSampleRate(onErrorSampleRate);
          }
        }

        options
            .getExperimental()
            .getSessionReplay()
            .setMaskAllText(readBool(metadata, logger, REPLAYS_MASK_ALL_TEXT, true));

        options
            .getExperimental()
            .getSessionReplay()
            .setMaskAllImages(readBool(metadata, logger, REPLAYS_MASK_ALL_IMAGES, true));
      }

      options
          .getLogger()
          .log(SentryLevel.INFO, "Retrieving configuration from AndroidManifest.xml");
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR, "Failed to read configuration from android manifest metadata.", e);
    }
  }

  private static boolean readBool(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final boolean defaultValue) {
    final boolean value = metadata.getBoolean(key, defaultValue);
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    return value;
  }

  @SuppressWarnings("deprecation")
  private static @Nullable Boolean readBoolNullable(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final @Nullable Boolean defaultValue) {
    if (metadata.getSerializable(key) != null) {
      final boolean nonNullDefault = defaultValue == null ? false : true;
      final boolean bool = metadata.getBoolean(key, nonNullDefault);
      logger.log(SentryLevel.DEBUG, key + " read: " + bool);
      return bool;
    } else {
      logger.log(SentryLevel.DEBUG, key + " used default " + defaultValue);
      return defaultValue;
    }
  }

  private static @Nullable String readString(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final @Nullable String defaultValue) {
    final String value = metadata.getString(key, defaultValue);
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    return value;
  }

  private static @NotNull String readStringNotNull(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final @NotNull String defaultValue) {
    final String value = metadata.getString(key, defaultValue);
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    return value;
  }

  private static @Nullable List<String> readList(
      final @NotNull Bundle metadata, final @NotNull ILogger logger, final @NotNull String key) {
    final String value = metadata.getString(key);
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    if (value != null) {
      return Arrays.asList(value.split(",", -1));
    } else {
      return null;
    }
  }

  private static @NotNull Double readDouble(
      final @NotNull Bundle metadata, final @NotNull ILogger logger, final @NotNull String key) {
    // manifest meta-data only reads float
    final Double value = ((Number) metadata.getFloat(key, metadata.getInt(key, -1))).doubleValue();
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    return value;
  }

  private static long readLong(
      final @NotNull Bundle metadata,
      final @NotNull ILogger logger,
      final @NotNull String key,
      final long defaultValue) {
    // manifest meta-data only reads int if the value is not big enough
    final long value = metadata.getInt(key, (int) defaultValue);
    logger.log(SentryLevel.DEBUG, key + " read: " + value);
    return value;
  }

  /**
   * Checks if auto init is enabled or disabled
   *
   * @param context the application context
   * @param logger the Logger interface
   * @return true if auto init is enabled or false otherwise
   */
  static boolean isAutoInit(final @NotNull Context context, final @NotNull ILogger logger) {
    Objects.requireNonNull(context, "The application context is required.");

    boolean autoInit = true;
    try {
      final Bundle metadata = getMetadata(context, logger, null);
      if (metadata != null) {
        autoInit = readBool(metadata, logger, AUTO_INIT, true);
      }
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Failed to read auto-init from android manifest metadata.", e);
    }
    return autoInit;
  }

  /**
   * Returns the Bundle attached from the given Context
   *
   * @param context the application context
   * @return the Bundle attached to the PackageManager
   * @throws PackageManager.NameNotFoundException if the package name is non-existent
   */
  private static @Nullable Bundle getMetadata(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @Nullable BuildInfoProvider buildInfoProvider)
      throws PackageManager.NameNotFoundException {
    final ApplicationInfo app =
        ContextUtils.getApplicationInfo(
            context,
            PackageManager.GET_META_DATA,
            buildInfoProvider != null ? buildInfoProvider : new BuildInfoProvider(logger));
    return app.metaData;
  }
}
