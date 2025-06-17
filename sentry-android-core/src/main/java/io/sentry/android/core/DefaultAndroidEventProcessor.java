package io.sentry.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import io.sentry.DateUtils;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.IpAddressUtils;
import io.sentry.NoOpLogger;
import io.sentry.SentryAttributeType;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryReplayEvent;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.App;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.HintUtils;
import io.sentry.util.LazyEvaluator;
import io.sentry.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class DefaultAndroidEventProcessor implements EventProcessor {

  @TestOnly final Context context;

  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull Future<DeviceInfoUtil> deviceInfoUtil;
  private final @NotNull LazyEvaluator<String> deviceFamily = new LazyEvaluator<>(() -> ContextUtils.getFamily(NoOpLogger.getInstance()));

  public DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryAndroidOptions options) {
    this.context =
        Objects.requireNonNull(
            ContextUtils.getApplicationContext(context), "The application context is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.options = Objects.requireNonNull(options, "The options object is required.");

    // don't ref. to method reference, theres a bug on it
    // noinspection Convert2MethodRef
    // some device info performs disk I/O, but it's result is cached, let's pre-cache it
    final @NotNull ExecutorService executorService = Executors.newSingleThreadExecutor();
    this.deviceInfoUtil =
        executorService.submit(() -> DeviceInfoUtil.getInstance(this.context, options));
    executorService.shutdown();
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      // we only set memory data if it's not a hard crash, when it's a hard crash the event is
      // enriched on restart, so non static data might be wrong, eg lowMemory or availMem will
      // be different if the App. crashes because of OOM.
      processNonCachedEvent(event, hint);
      setThreads(event, hint);
    }

    setCommons(event, true, applyScopeData);

    fixExceptionOrder(event);

    return event;
  }

  @Override
  public @Nullable SentryLogEvent process(@NotNull SentryLogEvent event) {
    setDevice(event);
    setOs(event);
    return event;
  }

  /**
   * The last exception is usually used for picking the issue title, but the convention is to send
   * inner exceptions first, e.g. [inner, outer] This doesn't work very well on Android, as some
   * hooks like Application.onCreate is wrapped by Android framework with a RuntimeException. Thus,
   * if the last exception is a RuntimeInit$MethodAndArgsCaller, reverse the order to get a better
   * issue title. This is a quick fix, for more details see: <a
   * href="https://github.com/getsentry/sentry/issues/64074">#64074</a> <a
   * href="https://github.com/getsentry/sentry/issues/59679">#59679</a> <a
   * href="https://github.com/getsentry/sentry/issues/64088">#64088</a>
   *
   * @param event the event to process
   */
  private static void fixExceptionOrder(final @NotNull SentryEvent event) {
    boolean reverseExceptions = false;

    final @Nullable List<SentryException> exceptions = event.getExceptions();
    if (exceptions != null && exceptions.size() > 1) {
      final @NotNull SentryException lastException = exceptions.get(exceptions.size() - 1);
      if ("java.lang".equals(lastException.getModule())) {
        final @Nullable SentryStackTrace stacktrace = lastException.getStacktrace();
        if (stacktrace != null) {
          final @Nullable List<SentryStackFrame> frames = stacktrace.getFrames();
          if (frames != null) {
            for (final @NotNull SentryStackFrame frame : frames) {
              if ("com.android.internal.os.RuntimeInit$MethodAndArgsCaller"
                  .equals(frame.getModule())) {
                reverseExceptions = true;
                break;
              }
            }
          }
        }
      }
    }

    if (reverseExceptions) {
      Collections.reverse(exceptions);
    }
  }

  private void setCommons(
      final @NotNull SentryBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    mergeUser(event);
    setDevice(event, errorEvent, applyScopeData);
    setSideLoadedInfo(event);
  }

  private boolean shouldApplyScopeData(
      final @NotNull SentryBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
      return false;
    }
  }

  private void mergeUser(final @NotNull SentryBaseEvent event) {
    @Nullable User user = event.getUser();
    if (user == null) {
      user = new User();
      event.setUser(user);
    }

    // userId should be set even if event is Cached as the userId is static and won't change anyway.
    if (user.getId() == null) {
      user.setId(Installation.id(context));
    }
    if (user.getIpAddress() == null && options.isSendDefaultPii()) {
      user.setIpAddress(IpAddressUtils.DEFAULT_IP_ADDRESS);
    }
  }

  private void setDevice(
      final @NotNull SentryBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    if (event.getContexts().getDevice() == null) {
      try {
        event
            .getContexts()
            .setDevice(deviceInfoUtil.get().collectDeviceInformation(errorEvent, applyScopeData));
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve device info", e);
      }
      mergeOS(event);
    }
  }

  private void mergeOS(final @NotNull SentryBaseEvent event) {
    final OperatingSystem currentOS = event.getContexts().getOperatingSystem();
    try {
      final OperatingSystem androidOS = deviceInfoUtil.get().getOperatingSystem();
      // make Android OS the main OS using the 'os' key
      event.getContexts().setOperatingSystem(androidOS);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve os system", e);
    }

    if (currentOS != null) {
      // add additional OS which was already part of the SentryEvent (eg Linux read from NDK)
      String osNameKey = currentOS.getName();
      if (osNameKey != null && !osNameKey.isEmpty()) {
        osNameKey = "os_" + osNameKey.trim().toLowerCase(Locale.ROOT);
      } else {
        osNameKey = "os_1";
      }
      event.getContexts().put(osNameKey, currentOS);
    }
  }

  private void setDevice(final @NotNull SentryLogEvent event) {
    try {
      event.setAttribute(
          "device.brand",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, Build.BRAND));
      event.setAttribute(
          "device.model",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, Build.MODEL));
      event.setAttribute(
          "device.family",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, deviceFamily.getValue()));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve device info", e);
    }
  }

  private void setOs(final @NotNull SentryLogEvent event) {
    try {
      event.setAttribute(
          "os.name",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, "Android"));
      event.setAttribute(
          "os.version",
          new SentryLogEventAttributeValue(SentryAttributeType.STRING, Build.VERSION.RELEASE));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve os system", e);
    }
  }

  // Data to be applied to events that was created in the running process
  private void processNonCachedEvent(
      final @NotNull SentryBaseEvent event, final @NotNull Hint hint) {
    App app = event.getContexts().getApp();
    if (app == null) {
      app = new App();
    }
    setAppExtras(app, hint);
    setPackageInfo(event, app);
    event.getContexts().setApp(app);
  }

  private void setThreads(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (event.getThreads() != null) {
      final boolean isHybridSDK = HintUtils.isFromHybridSdk(hint);

      for (final SentryThread thread : event.getThreads()) {
        final boolean isMainThread = AndroidThreadChecker.getInstance().isMainThread(thread);

        // TODO: Fix https://github.com/getsentry/team-mobile/issues/47
        if (thread.isCurrent() == null) {
          thread.setCurrent(isMainThread);
        }

        // This should not be set by Hybrid SDKs since they have their own threading model
        if (!isHybridSDK && thread.isMain() == null) {
          thread.setMain(isMainThread);
        }
      }
    }
  }

  private void setPackageInfo(final @NotNull SentryBaseEvent event, final @NotNull App app) {
    final PackageInfo packageInfo =
        ContextUtils.getPackageInfo(
            context, PackageManager.GET_PERMISSIONS, options.getLogger(), buildInfoProvider);
    if (packageInfo != null) {
      String versionCode = ContextUtils.getVersionCode(packageInfo, buildInfoProvider);

      setDist(event, versionCode);

      @Nullable DeviceInfoUtil deviceInfoUtil = null;
      try {
        deviceInfoUtil = this.deviceInfoUtil.get();
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve device info", e);
      }

      ContextUtils.setAppPackageInfo(packageInfo, buildInfoProvider, deviceInfoUtil, app);
    }
  }

  private void setDist(final @NotNull SentryBaseEvent event, final @NotNull String versionCode) {
    if (event.getDist() == null) {
      event.setDist(versionCode);
    }
  }

  private void setAppExtras(final @NotNull App app, final @NotNull Hint hint) {
    app.setAppName(ContextUtils.getApplicationName(context));
    final @NotNull TimeSpan appStartTimeSpan =
        AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options);
    if (appStartTimeSpan.hasStarted()) {
      app.setAppStartTime(DateUtils.toUtilDate(appStartTimeSpan.getStartTimestamp()));
    }

    // This should not be set by Hybrid SDKs since they have their own app's lifecycle
    if (!HintUtils.isFromHybridSdk(hint) && app.getInForeground() == null) {
      // This feature depends on the AppLifecycleIntegration being installed, so only if
      // enableAutoSessionTracking or enableAppLifecycleBreadcrumbs are enabled.
      final @Nullable Boolean isBackground = AppState.getInstance().isInBackground();
      if (isBackground != null) {
        app.setInForeground(!isBackground);
      }
    }
  }

  /**
   * Sets the default user which contains only the userId.
   *
   * @return the User object
   */
  public @NotNull User getDefaultUser(final @NotNull Context context) {
    final @NotNull User user = new User();
    user.setId(Installation.id(context));
    return user;
  }

  private void setSideLoadedInfo(final @NotNull SentryBaseEvent event) {
    try {
      final ContextUtils.SideLoadedInfo sideLoadedInfo = deviceInfoUtil.get().getSideLoadedInfo();
      if (sideLoadedInfo != null) {
        final @NotNull Map<String, String> tags = sideLoadedInfo.asTags();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
          event.setTag(entry.getKey(), entry.getValue());
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting side loaded info.", e);
    }
  }

  @Override
  public @NotNull SentryTransaction process(
      final @NotNull SentryTransaction transaction, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(transaction, hint);

    if (applyScopeData) {
      processNonCachedEvent(transaction, hint);
    }

    setCommons(transaction, false, applyScopeData);

    return transaction;
  }

  @Override
  public @NotNull SentryReplayEvent process(
      final @NotNull SentryReplayEvent event, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      processNonCachedEvent(event, hint);
    }

    setCommons(event, false, applyScopeData);

    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 8000L;
  }
}
