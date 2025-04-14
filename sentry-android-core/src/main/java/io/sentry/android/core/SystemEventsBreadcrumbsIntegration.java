package io.sentry.android.core;

import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_CAMERA_BUTTON;
import static android.content.Intent.ACTION_CONFIGURATION_CHANGED;
import static android.content.Intent.ACTION_DATE_CHANGED;
import static android.content.Intent.ACTION_DEVICE_STORAGE_LOW;
import static android.content.Intent.ACTION_DEVICE_STORAGE_OK;
import static android.content.Intent.ACTION_DOCK_EVENT;
import static android.content.Intent.ACTION_DREAMING_STARTED;
import static android.content.Intent.ACTION_DREAMING_STOPPED;
import static android.content.Intent.ACTION_INPUT_METHOD_CHANGED;
import static android.content.Intent.ACTION_LOCALE_CHANGED;
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
import java.util.Arrays;
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

  private final @NotNull String[] actions;
  private boolean isClosed = false;
  private final @NotNull Object startLock = new Object();

  public SystemEventsBreadcrumbsIntegration(final @NotNull Context context) {
    this(context, getDefaultActionsInternal());
  }

  private SystemEventsBreadcrumbsIntegration(
      final @NotNull Context context, final @NotNull String[] actions) {
    this.context = ContextUtils.getApplicationContext(context);
    this.actions = actions;
  }

  public SystemEventsBreadcrumbsIntegration(
      final @NotNull Context context, final @NotNull List<String> actions) {
    this.context = ContextUtils.getApplicationContext(context);
    this.actions = new String[actions.size()];
    actions.toArray(this.actions);
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

  public static @NotNull List<String> getDefaultActions() {
    return Arrays.asList(getDefaultActionsInternal());
  }

  @SuppressWarnings("deprecation")
  private static @NotNull String[] getDefaultActionsInternal() {
    final String[] actions = new String[19];
    actions[0] = ACTION_SHUTDOWN;
    actions[1] = ACTION_AIRPLANE_MODE_CHANGED;
    actions[2] = ACTION_BATTERY_CHANGED;
    actions[3] = ACTION_CAMERA_BUTTON;
    actions[4] = ACTION_CONFIGURATION_CHANGED;
    actions[5] = ACTION_DATE_CHANGED;
    actions[6] = ACTION_DEVICE_STORAGE_LOW;
    actions[7] = ACTION_DEVICE_STORAGE_OK;
    actions[8] = ACTION_DOCK_EVENT;
    actions[9] = ACTION_DREAMING_STARTED;
    actions[10] = ACTION_DREAMING_STOPPED;
    actions[11] = ACTION_INPUT_METHOD_CHANGED;
    actions[12] = ACTION_LOCALE_CHANGED;
    actions[13] = ACTION_SCREEN_OFF;
    actions[14] = ACTION_SCREEN_ON;
    actions[15] = ACTION_TIMEZONE_CHANGED;
    actions[16] = ACTION_TIME_CHANGED;
    actions[17] = "android.os.action.DEVICE_IDLE_MODE_CHANGED";
    actions[18] = "android.os.action.POWER_SAVE_MODE_CHANGED";
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
        // ignored
      }
    }

    // in theory this should be ThreadLocal, but we won't have more than 1 thread accessing it,
    // so we save some memory here and CPU cycles. 64 is because all intent actions we subscribe for
    // are less than 64 chars. We also don't care about encoding as those are always UTF.
    // TODO: _MULTI_THREADED_EXECUTOR_
    private final char[] buf = new char[64];

    @TestOnly
    @Nullable
    String getStringAfterDotFast(final @Nullable String str) {
      if (str == null) {
        return null;
      }

      final int len = str.length();
      int bufIndex = buf.length;

      // the idea here is to iterate from the end of the string and copy the characters to a
      // pre-allocated buffer in reverse order. When we find a dot, we create a new string
      // from the buffer. This way we use a fixed size buffer and do a bare minimum of iterations.
      for (int i = len - 1; i >= 0; i--) {
        final char c = str.charAt(i);
        if (c == '.') {
          return new String(buf, bufIndex, buf.length - bufIndex);
        }
        if (bufIndex == 0) {
          // Overflow — fallback to safe version
          return StringUtils.getStringAfterDot(str);
        }
        buf[--bufIndex] = c;
      }

      // No dot found — return original
      return str;
    }

    private @NotNull Breadcrumb createBreadcrumb(
        final long timeMs,
        final @NotNull Intent intent,
        final @Nullable String action,
        boolean isBatteryChanged) {
      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      final String shortAction = getStringAfterDotFast(action);
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
