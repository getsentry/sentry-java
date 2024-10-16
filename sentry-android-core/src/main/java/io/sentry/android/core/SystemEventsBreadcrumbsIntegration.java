package io.sentry.android.core;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_DELETED;
import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_DISABLED;
import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_ENABLED;
import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.content.Intent.ACTION_APP_ERROR;
import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_BATTERY_LOW;
import static android.content.Intent.ACTION_BATTERY_OKAY;
import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_BUG_REPORT;
import static android.content.Intent.ACTION_CAMERA_BUTTON;
import static android.content.Intent.ACTION_CONFIGURATION_CHANGED;
import static android.content.Intent.ACTION_DATE_CHANGED;
import static android.content.Intent.ACTION_DEVICE_STORAGE_LOW;
import static android.content.Intent.ACTION_DEVICE_STORAGE_OK;
import static android.content.Intent.ACTION_DOCK_EVENT;
import static android.content.Intent.ACTION_INPUT_METHOD_CHANGED;
import static android.content.Intent.ACTION_LOCALE_CHANGED;
import static android.content.Intent.ACTION_MEDIA_BAD_REMOVAL;
import static android.content.Intent.ACTION_MEDIA_MOUNTED;
import static android.content.Intent.ACTION_MEDIA_UNMOUNTABLE;
import static android.content.Intent.ACTION_MEDIA_UNMOUNTED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.content.Intent.ACTION_REBOOT;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.content.Intent.ACTION_SHUTDOWN;
import static android.content.Intent.ACTION_TIMEZONE_CHANGED;
import static android.content.Intent.ACTION_TIME_CHANGED;
import static io.sentry.TypeCheckHint.ANDROID_INTENT;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.util.Objects;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SystemEventsBreadcrumbsIntegration implements Integration, Closeable {

  private final @NotNull Context context;

  @TestOnly @Nullable SystemEventsBroadcastReceiver receiver;

  private @Nullable SentryAndroidOptions options;

  private final @NotNull List<String> actions;
  private boolean isClosed = false;
  private final @NotNull Object startLock = new Object();

  public SystemEventsBreadcrumbsIntegration(final @NotNull Context context) {
    this(context, getDefaultActions());
  }

  public SystemEventsBreadcrumbsIntegration(
      final @NotNull Context context, final @NotNull List<String> actions) {
    this.context =
        Objects.requireNonNull(ContextUtils.getApplicationContext(context), "Context is required");
    this.actions = Objects.requireNonNull(actions, "Actions list is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "SystemEventsBreadcrumbsIntegration enabled: %s",
            this.options.isEnableSystemEventBreadcrumbs());

    if (this.options.isEnableSystemEventBreadcrumbs()) {

      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  synchronized (startLock) {
                    if (!isClosed) {
                      startSystemEventsReceiver(hub, (SentryAndroidOptions) options);
                    }
                  }
                });
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Failed to start SystemEventsBreadcrumbsIntegration on executor thread.",
                e);
      }
    }
  }

  private void startSystemEventsReceiver(
      final @NotNull IHub hub, final @NotNull SentryAndroidOptions options) {
    receiver = new SystemEventsBroadcastReceiver(hub, options);
    final IntentFilter filter = new IntentFilter();
    for (String item : actions) {
      filter.addAction(item);
    }
    try {
      // registerReceiver can throw SecurityException but it's not documented in the official docs
      ContextUtils.registerReceiver(context, options, receiver, filter);
      options.getLogger().log(SentryLevel.DEBUG, "SystemEventsBreadcrumbsIntegration installed.");
      addIntegrationToSdkVersion("SystemEventsBreadcrumbs");
    } catch (Throwable e) {
      options.setEnableSystemEventBreadcrumbs(false);
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to initialize SystemEventsBreadcrumbsIntegration.", e);
    }
  }

  @SuppressWarnings("deprecation")
  private static @NotNull List<String> getDefaultActions() {
    final List<String> actions = new ArrayList<>();
    actions.add(ACTION_APPWIDGET_DELETED);
    actions.add(ACTION_APPWIDGET_DISABLED);
    actions.add(ACTION_APPWIDGET_ENABLED);
    actions.add("android.appwidget.action.APPWIDGET_HOST_RESTORED");
    actions.add("android.appwidget.action.APPWIDGET_RESTORED");
    actions.add(ACTION_APPWIDGET_UPDATE);
    actions.add("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS");
    actions.add(ACTION_POWER_CONNECTED);
    actions.add(ACTION_POWER_DISCONNECTED);
    actions.add(ACTION_SHUTDOWN);
    actions.add(ACTION_AIRPLANE_MODE_CHANGED);
    actions.add(ACTION_BATTERY_LOW);
    actions.add(ACTION_BATTERY_OKAY);
    actions.add(ACTION_BATTERY_CHANGED);
    actions.add(ACTION_BOOT_COMPLETED);
    actions.add(ACTION_CAMERA_BUTTON);
    actions.add(ACTION_CONFIGURATION_CHANGED);
    actions.add("android.intent.action.CONTENT_CHANGED");
    actions.add(ACTION_DATE_CHANGED);
    actions.add(ACTION_DEVICE_STORAGE_LOW);
    actions.add(ACTION_DEVICE_STORAGE_OK);
    actions.add(ACTION_DOCK_EVENT);
    actions.add("android.intent.action.DREAMING_STARTED");
    actions.add("android.intent.action.DREAMING_STOPPED");
    actions.add(ACTION_INPUT_METHOD_CHANGED);
    actions.add(ACTION_LOCALE_CHANGED);
    actions.add(ACTION_REBOOT);
    actions.add(ACTION_SCREEN_OFF);
    actions.add(ACTION_SCREEN_ON);
    actions.add(ACTION_TIMEZONE_CHANGED);
    actions.add(ACTION_TIME_CHANGED);
    actions.add("android.os.action.DEVICE_IDLE_MODE_CHANGED");
    actions.add("android.os.action.POWER_SAVE_MODE_CHANGED");
    // The user pressed the "Report" button in the crash/ANR dialog.
    actions.add(ACTION_APP_ERROR);
    // Show activity for reporting a bug.
    actions.add(ACTION_BUG_REPORT);

    // consider if somebody mounted or ejected a sdcard
    actions.add(ACTION_MEDIA_BAD_REMOVAL);
    actions.add(ACTION_MEDIA_MOUNTED);
    actions.add(ACTION_MEDIA_UNMOUNTABLE);
    actions.add(ACTION_MEDIA_UNMOUNTED);

    return actions;
  }

  @Override
  public void close() throws IOException {
    synchronized (startLock) {
      isClosed = true;
    }
    if (receiver != null) {
      context.unregisterReceiver(receiver);
      receiver = null;

      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "SystemEventsBreadcrumbsIntegration remove.");
      }
    }
  }

  static final class SystemEventsBroadcastReceiver extends BroadcastReceiver {

    private static final long DEBOUNCE_WAIT_TIME_MS = 60 * 1000;
    private final @NotNull IHub hub;
    private final @NotNull SentryAndroidOptions options;
    private final @NotNull Debouncer batteryChangedDebouncer =
        new Debouncer(AndroidCurrentDateProvider.getInstance(), DEBOUNCE_WAIT_TIME_MS, 0);

    SystemEventsBroadcastReceiver(
        final @NotNull IHub hub, final @NotNull SentryAndroidOptions options) {
      this.hub = hub;
      this.options = options;
    }

    @Override
    public void onReceive(final Context context, final @NotNull Intent intent) {
      final @Nullable String action = intent.getAction();
      final boolean isBatteryChanged = ACTION_BATTERY_CHANGED.equals(action);

      // aligning with iOS which only captures battery status changes every minute at maximum
      if (isBatteryChanged && batteryChangedDebouncer.checkForDebounce()) {
        return;
      }

      final long now = System.currentTimeMillis();
      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  final Breadcrumb breadcrumb =
                      createBreadcrumb(now, intent, action, isBatteryChanged);
                  final Hint hint = new Hint();
                  hint.set(ANDROID_INTENT, intent);
                  hub.addBreadcrumb(breadcrumb, hint);
                });
      } catch (Throwable t) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, t, "Failed to submit system event breadcrumb action.");
      }
    }

    private @NotNull Breadcrumb createBreadcrumb(
        final long timeMs,
        final @NotNull Intent intent,
        final @Nullable String action,
        boolean isBatteryChanged) {
      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      final String shortAction = StringUtils.getStringAfterDot(action);
      if (shortAction != null) {
        breadcrumb.setData("action", shortAction);
      }

      if (isBatteryChanged) {
        final Float batteryLevel = DeviceInfoUtil.getBatteryLevel(intent, options);
        if (batteryLevel != null) {
          breadcrumb.setData("level", batteryLevel);
        }
        final Boolean isCharging = DeviceInfoUtil.isCharging(intent, options);
        if (isCharging != null) {
          breadcrumb.setData("charging", isCharging);
        }
      } else {
        final Bundle extras = intent.getExtras();
        final Map<String, String> newExtras = new HashMap<>();
        if (extras != null && !extras.isEmpty()) {
          for (String item : extras.keySet()) {
            try {
              @SuppressWarnings("deprecation")
              Object value = extras.get(item);
              if (value != null) {
                newExtras.put(item, value.toString());
              }
            } catch (Throwable exception) {
              options
                  .getLogger()
                  .log(
                      SentryLevel.ERROR,
                      exception,
                      "%s key of the %s action threw an error.",
                      item,
                      action);
            }
          }
          breadcrumb.setData("extras", newExtras);
        }
      }
      breadcrumb.setLevel(SentryLevel.INFO);
      return breadcrumb;
    }
  }
}
