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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SystemEventsBreadcrumbsIntegration
    implements Integration, Closeable, AppState.AppStateListener {

  private final @NotNull Context context;

  @TestOnly @Nullable volatile SystemEventsBroadcastReceiver receiver;

  private @Nullable SentryAndroidOptions options;

  private @Nullable IScopes scopes;

  private final @NotNull String[] actions;
  private volatile boolean isClosed = false;
  private volatile boolean isStopped = false;
  private volatile IntentFilter filter = null;
  private volatile HandlerThread handlerThread = null;
  private final @NotNull AtomicBoolean isReceiverRegistered = new AtomicBoolean(false);
  private final @NotNull AutoClosableReentrantLock receiverLock = new AutoClosableReentrantLock();
  // Track previous battery state to avoid duplicate breadcrumbs when values haven't changed
  private @Nullable BatteryState previousBatteryState;

  public SystemEventsBreadcrumbsIntegration(final @NotNull Context context) {
    this(context, getDefaultActionsInternal());
  }

  SystemEventsBreadcrumbsIntegration(
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
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");
    this.scopes = scopes;

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "SystemEventsBreadcrumbsIntegration enabled: %s",
            this.options.isEnableSystemEventBreadcrumbs());

    if (this.options.isEnableSystemEventBreadcrumbs()) {
      AppState.getInstance().addAppStateListener(this);

      if (ContextUtils.isForegroundImportance()) {
        registerReceiver(this.scopes, this.options);
      }
    }
  }

  private void registerReceiver(
      final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {

    if (!options.isEnableSystemEventBreadcrumbs()) {
      return;
    }

    if (isClosed || isStopped || receiver != null) {
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try (final @NotNull ISentryLifecycleToken ignored = receiverLock.acquire()) {
                  if (isClosed || isStopped || receiver != null) {
                    return;
                  }

                  receiver = new SystemEventsBroadcastReceiver(scopes, options);
                  if (filter == null) {
                    filter = new IntentFilter();
                    for (String item : actions) {
                      filter.addAction(item);
                    }
                  }
                  if (handlerThread == null) {
                    handlerThread =
                        new HandlerThread(
                            "SystemEventsReceiver", Process.THREAD_PRIORITY_BACKGROUND);
                    handlerThread.start();
                  }
                  try {
                    // registerReceiver can throw SecurityException but it's not documented in the
                    // official docs

                    // onReceive will be called on this handler thread
                    final @NotNull Handler handler = new Handler(handlerThread.getLooper());
                    ContextUtils.registerReceiver(context, options, receiver, filter, handler);
                    if (!isReceiverRegistered.getAndSet(true)) {
                      options
                          .getLogger()
                          .log(SentryLevel.DEBUG, "SystemEventsBreadcrumbsIntegration installed.");
                      addIntegrationToSdkVersion("SystemEventsBreadcrumbs");
                    }
                  } catch (Throwable e) {
                    options.setEnableSystemEventBreadcrumbs(false);
                    options
                        .getLogger()
                        .log(
                            SentryLevel.ERROR,
                            "Failed to initialize SystemEventsBreadcrumbsIntegration.",
                            e);
                  }
                }
              });
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Failed to start SystemEventsBreadcrumbsIntegration on executor thread.");
    }
  }

  private void unregisterReceiver() {
    if (options == null) {
      return;
    }

    options
        .getExecutorService()
        .submit(
            () -> {
              final @Nullable SystemEventsBroadcastReceiver receiverRef;
              try (final @NotNull ISentryLifecycleToken ignored = receiverLock.acquire()) {
                isStopped = true;
                receiverRef = receiver;
                receiver = null;
              }

              if (receiverRef != null) {
                context.unregisterReceiver(receiverRef);
              }
            });
  }

  @Override
  public void close() throws IOException {
    try (final @NotNull ISentryLifecycleToken ignored = receiverLock.acquire()) {
      isClosed = true;
      filter = null;
      if (handlerThread != null) {
        handlerThread.quit();
      }
      handlerThread = null;
    }

    AppState.getInstance().removeAppStateListener(this);
    unregisterReceiver();

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "SystemEventsBreadcrumbsIntegration removed.");
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
  public void onForeground() {
    if (scopes == null || options == null) {
      return;
    }

    isStopped = false;

    registerReceiver(scopes, options);
  }

  @Override
  public void onBackground() {
    unregisterReceiver();
  }

  final class SystemEventsBroadcastReceiver extends BroadcastReceiver {

    private static final long DEBOUNCE_WAIT_TIME_MS = 60 * 1000;
    private final @NotNull IScopes scopes;
    private final @NotNull SentryAndroidOptions options;
    private final @NotNull Debouncer batteryChangedDebouncer =
        new Debouncer(AndroidCurrentDateProvider.getInstance(), DEBOUNCE_WAIT_TIME_MS, 0);

    SystemEventsBroadcastReceiver(
        final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
      this.scopes = scopes;
      this.options = options;
    }

    @Override
    public void onReceive(final Context context, final @NotNull Intent intent) {
      final @Nullable String action = intent.getAction();
      final boolean isBatteryChanged = ACTION_BATTERY_CHANGED.equals(action);

      @Nullable BatteryState batteryState = null;
      if (isBatteryChanged) {
        if (batteryChangedDebouncer.checkForDebounce()) {
          // aligning with iOS which only captures battery status changes every minute at maximum
          return;
        }

        // For battery changes, check if the actual values have changed
        final @Nullable Float batteryLevel = DeviceInfoUtil.getBatteryLevel(intent, options);
        final @Nullable Integer currentBatteryLevel =
            batteryLevel != null ? batteryLevel.intValue() : null;
        final @Nullable Boolean currentChargingState = DeviceInfoUtil.isCharging(intent, options);
        batteryState = new BatteryState(currentBatteryLevel, currentChargingState);

        // Only create breadcrumb if battery state has actually changed
        if (batteryState.equals(previousBatteryState)) {
          return;
        }

        previousBatteryState = batteryState;
      }

      final BatteryState state = batteryState;
      final long now = System.currentTimeMillis();
      final Breadcrumb breadcrumb = createBreadcrumb(now, intent, action, state);
      final Hint hint = new Hint();
      hint.set(ANDROID_INTENT, intent);
      scopes.addBreadcrumb(breadcrumb, hint);
    }

    // in theory this should be ThreadLocal, but we won't have more than 1 thread accessing it,
    // so we save some memory here and CPU cycles. 64 is because all intent actions we subscribe for
    // are less than 64 chars. We also don't care about encoding as those are always UTF.
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
        final @Nullable BatteryState batteryState) {
      final Breadcrumb breadcrumb = new Breadcrumb(timeMs);
      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      final String shortAction = getStringAfterDotFast(action);
      if (shortAction != null) {
        breadcrumb.setData("action", shortAction);
      }

      if (batteryState != null) {
        if (batteryState.level != null) {
          breadcrumb.setData("level", batteryState.level);
        }
        if (batteryState.charging != null) {
          breadcrumb.setData("charging", batteryState.charging);
        }
      } else if (options.isEnableSystemEventBreadcrumbsExtras()) {
        final Bundle extras = intent.getExtras();
        if (extras != null && !extras.isEmpty()) {
          final Map<String, String> newExtras = new HashMap<>(extras.size());
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

  static final class BatteryState {
    private final @Nullable Integer level;
    private final @Nullable Boolean charging;

    BatteryState(final @Nullable Integer level, final @Nullable Boolean charging) {
      this.level = level;
      this.charging = charging;
    }

    @Override
    public boolean equals(final @Nullable Object other) {
      if (!(other instanceof BatteryState)) return false;
      BatteryState that = (BatteryState) other;
      return Objects.equals(level, that.level) && Objects.equals(charging, that.charging);
    }

    @Override
    public int hashCode() {
      return Objects.hash(level, charging);
    }
  }
}
