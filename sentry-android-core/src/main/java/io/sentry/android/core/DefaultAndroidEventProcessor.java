package io.sentry.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import io.sentry.DateUtils;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryReplayEvent;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.protocol.App;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
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

  public DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryAndroidOptions options) {
    this.context = Objects.requireNonNull(context, "The application context is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.options = Objects.requireNonNull(options, "The options object is required.");

    // don't ref. to method reference, theres a bug on it
    // noinspection Convert2MethodRef
    // some device info performs disk I/O, but it's result is cached, let's pre-cache it
    final @NotNull ExecutorService executorService = Executors.newSingleThreadExecutor();
    this.deviceInfoUtil =
        executorService.submit(() -> DeviceInfoUtil.getInstance(context, options));
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

    return event;
  }

  @Override
  public @Nullable SentryReplayEvent process(@NotNull SentryReplayEvent event, @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      // we only set memory data if it's not a hard crash, when it's a hard crash the event is
      // enriched on restart, so non static data might be wrong, eg lowMemory or availMem will
      // be different if the App. crashes because of OOM.
      processNonCachedEvent(event, hint);
    }

    setCommons(event, true, applyScopeData);

    return event;
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
    // userId should be set even if event is Cached as the userId is static and won't change anyway.
    final User user = event.getUser();
    if (user == null) {
      event.setUser(getDefaultUser(context));
    } else if (user.getId() == null) {
      user.setId(Installation.id(context));
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
        final boolean isMainThread = AndroidMainThreadChecker.getInstance().isMainThread(thread);

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
      ContextUtils.setAppPackageInfo(packageInfo, buildInfoProvider, app);
    }
  }

  private void setDist(final @NotNull SentryBaseEvent event, final @NotNull String versionCode) {
    if (event.getDist() == null) {
      event.setDist(versionCode);
    }
  }

  private void setAppExtras(final @NotNull App app, final @NotNull Hint hint) {
    app.setAppName(ContextUtils.getApplicationName(context, options.getLogger()));
    app.setAppStartTime(DateUtils.toUtilDate(AppStartState.getInstance().getAppStartTime()));

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
}
