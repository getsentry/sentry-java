package io.sentry.android.core;

import static android.content.Context.SENSOR_SERVICE;
import static io.sentry.TypeCheckHint.ANDROID_SENSOR_EVENT;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class TempSensorBreadcrumbsIntegration
    implements Integration, Closeable, SensorEventListener {

  private final @NotNull Context context;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  @TestOnly @Nullable SensorManager sensorManager;
  private boolean isClosed = false;
  private final @NotNull Object startLock = new Object();

  public TempSensorBreadcrumbsIntegration(final @NotNull Context context) {
    this.context =
        Objects.requireNonNull(ContextUtils.getApplicationContext(context), "Context is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.hub = Objects.requireNonNull(hub, "Hub is required");
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "enableSystemEventsBreadcrumbs enabled: %s",
            this.options.isEnableSystemEventBreadcrumbs());

    if (this.options.isEnableSystemEventBreadcrumbs()) {

      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  synchronized (startLock) {
                    if (!isClosed) {
                      startSensorListener(options);
                    }
                  }
                });
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Failed to start TempSensorBreadcrumbsIntegration on executor thread.",
                e);
      }
    }
  }

  private void startSensorListener(final @NotNull SentryOptions options) {
    try {
      sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
      if (sensorManager != null) {
        final Sensor defaultSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (defaultSensor != null) {
          sensorManager.registerListener(this, defaultSensor, SensorManager.SENSOR_DELAY_NORMAL);

          options.getLogger().log(SentryLevel.DEBUG, "TempSensorBreadcrumbsIntegration installed.");
          addIntegrationToSdkVersion("TempSensorBreadcrumbs");
        } else {
          options.getLogger().log(SentryLevel.INFO, "TYPE_AMBIENT_TEMPERATURE is not available.");
        }
      } else {
        options.getLogger().log(SentryLevel.INFO, "SENSOR_SERVICE is not available.");
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Failed to init. the SENSOR_SERVICE.");
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (startLock) {
      isClosed = true;
    }
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      sensorManager = null;

      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "TempSensorBreadcrumbsIntegration removed.");
      }
    }
  }

  @Override
  public void onSensorChanged(final @NotNull SensorEvent event) {
    final float[] values = event.values;
    // return if data is not available or zero'ed
    if (values == null || values.length == 0 || values[0] == 0f) {
      return;
    }

    if (hub != null) {
      final Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setType("system");
      breadcrumb.setCategory("device.event");
      breadcrumb.setData("action", "TYPE_AMBIENT_TEMPERATURE");
      breadcrumb.setData("accuracy", event.accuracy);
      breadcrumb.setData("timestamp", event.timestamp);
      breadcrumb.setLevel(SentryLevel.INFO);
      breadcrumb.setData("degree", event.values[0]); // Celsius

      final Hint hint = new Hint();
      hint.set(ANDROID_SENSOR_EVENT, event);

      hub.addBreadcrumb(breadcrumb, hint);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
