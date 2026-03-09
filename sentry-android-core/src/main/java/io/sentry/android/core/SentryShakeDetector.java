package io.sentry.android.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects shake gestures using the device's accelerometer.
 *
 * <p>The accelerometer sensor (TYPE_ACCELEROMETER) does NOT require any special permissions on
 * Android. The BODY_SENSORS permission is only needed for heart rate and similar body sensors.
 *
 * <p>Requires at least {@link #SHAKE_COUNT_THRESHOLD} accelerometer readings above {@link
 * #SHAKE_THRESHOLD_GRAVITY} within {@link #SHAKE_WINDOW_MS} to trigger a shake event.
 *
 * <p>Sensor events are delivered on a background {@link HandlerThread} to avoid polluting the main
 * thread.
 */
@ApiStatus.Internal
public final class SentryShakeDetector implements SensorEventListener {

  private static final float SHAKE_THRESHOLD_GRAVITY = 2.7f;
  private static final int SHAKE_WINDOW_MS = 1500;
  private static final int SHAKE_COUNT_THRESHOLD = 2;
  private static final int SHAKE_COOLDOWN_MS = 1000;

  private @Nullable SensorManager sensorManager;
  private @Nullable Sensor accelerometer;
  private @Nullable HandlerThread handlerThread;
  private final @NotNull AtomicLong lastShakeTimestamp = new AtomicLong(0);
  private volatile @Nullable Listener listener;
  private final @NotNull ILogger logger;

  private int shakeCount = 0;
  private long firstShakeTimestamp = 0;

  public interface Listener {
    void onShake();
  }

  public SentryShakeDetector(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  /**
   * Initializes the sensor manager and accelerometer sensor. This is separated from start() so the
   * values can be resolved once and reused across activity transitions.
   */
  void init(final @NotNull Context context) {
    if (sensorManager == null) {
      sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }
    if (sensorManager != null && accelerometer == null) {
      accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false);
    }
  }

  public void start(final @NotNull Context context, final @NotNull Listener shakeListener) {
    this.listener = shakeListener;
    init(context);
    if (sensorManager == null) {
      logger.log(SentryLevel.WARNING, "SensorManager is not available. Shake detection disabled.");
      return;
    }
    if (accelerometer == null) {
      logger.log(
          SentryLevel.WARNING, "Accelerometer sensor not available. Shake detection disabled.");
      return;
    }
    handlerThread = new HandlerThread("sentry-shake");
    handlerThread.start();
    final Handler handler = new Handler(handlerThread.getLooper());
    sensorManager.registerListener(
        this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, handler);
  }

  public void stop() {
    listener = null;
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
    }
    if (handlerThread != null) {
      handlerThread.quitSafely();
      handlerThread = null;
    }
  }

  @Override
  public void onSensorChanged(final @NotNull SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
      return;
    }
    float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
    float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
    float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
    double gForceSquared = gX * gX + gY * gY + gZ * gZ;
    if (gForceSquared > SHAKE_THRESHOLD_GRAVITY * SHAKE_THRESHOLD_GRAVITY) {
      long now = SystemClock.elapsedRealtime();

      // Reset counter if outside the detection window
      if (now - firstShakeTimestamp > SHAKE_WINDOW_MS) {
        shakeCount = 0;
        firstShakeTimestamp = now;
      }

      shakeCount++;

      if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
        // Enforce cooldown so we don't fire repeatedly
        long lastShake = lastShakeTimestamp.get();
        if (now - lastShake > SHAKE_COOLDOWN_MS) {
          lastShakeTimestamp.set(now);
          shakeCount = 0;
          final @Nullable Listener currentListener = listener;
          if (currentListener != null) {
            currentListener.onShake();
          }
        }
      }
    }
  }

  @Override
  public void onAccuracyChanged(final @NotNull Sensor sensor, final int accuracy) {
    // Not needed for shake detection.
  }
}
