package io.sentry.android.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects shake gestures using the device's accelerometer.
 *
 * <p>The accelerometer sensor (TYPE_ACCELEROMETER) does NOT require any special permissions on
 * Android. The BODY_SENSORS permission is only needed for heart rate and similar body sensors.
 */
public final class SentryShakeDetector implements SensorEventListener {

  private static final float SHAKE_THRESHOLD_GRAVITY = 2.7f;
  private static final int SHAKE_COOLDOWN_MS = 1000;

  private @Nullable SensorManager sensorManager;
  private long lastShakeTimestamp = 0;
  private @Nullable Listener listener;
  private final @NotNull ILogger logger;

  public interface Listener {
    void onShake();
  }

  public SentryShakeDetector(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  public void start(final @NotNull Context context, final @NotNull Listener shakeListener) {
    this.listener = shakeListener;
    sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    if (sensorManager == null) {
      logger.log(SentryLevel.WARNING, "SensorManager is not available. Shake detection disabled.");
      return;
    }
    Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accelerometer == null) {
      logger.log(
          SentryLevel.WARNING, "Accelerometer sensor not available. Shake detection disabled.");
      return;
    }
    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
  }

  public void stop() {
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
      sensorManager = null;
    }
    listener = null;
  }

  @Override
  public void onSensorChanged(final @NotNull SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
      return;
    }
    float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
    float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
    float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
    double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);
    if (gForce > SHAKE_THRESHOLD_GRAVITY) {
      long now = System.currentTimeMillis();
      if (now - lastShakeTimestamp > SHAKE_COOLDOWN_MS) {
        lastShakeTimestamp = now;
        if (listener != null) {
          listener.onShake();
        }
      }
    }
  }

  @Override
  public void onAccuracyChanged(final @NotNull Sensor sensor, final int accuracy) {
    // Not needed for shake detection.
  }
}
