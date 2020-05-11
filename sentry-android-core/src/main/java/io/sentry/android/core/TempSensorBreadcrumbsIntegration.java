package io.sentry.android.core;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.annotation.Nullable;
import io.sentry.core.Breadcrumb;
import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class TempSensorBreadcrumbsIntegration
    implements Integration, Closeable, SensorEventListener {

  private final @NotNull Context context;
  private @Nullable IHub hub;
  private @Nullable SentryAndroidOptions options;

  @TestOnly @Nullable SensorManager sensorManager;

  public TempSensorBreadcrumbsIntegration(final @NotNull Context context) {
    this.context = Objects.requireNonNull(context, "Context is required");
  }

  @SuppressWarnings("deprecation")
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
      sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
      if (sensorManager != null) {
        final Sensor defaultSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (defaultSensor != null) {
          sensorManager.registerListener(this, defaultSensor, SensorManager.SENSOR_DELAY_NORMAL);

          options.getLogger().log(SentryLevel.DEBUG, "TempSensorBreadcrumbsIntegration installed.");
        } else {
          this.options
              .getLogger()
              .log(SentryLevel.INFO, "TYPE_AMBIENT_TEMPERATURE is not available.");
        }
      } else {
        this.options.getLogger().log(SentryLevel.INFO, "SENSOR_SERVICE is not available.");
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      sensorManager = null;

      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "TempSensorBreadcrumbsIntegration removed.");
      }
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
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
      hub.addBreadcrumb(breadcrumb);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
