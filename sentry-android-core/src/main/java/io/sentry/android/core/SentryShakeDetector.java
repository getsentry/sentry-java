// Adapted from Square's Seismic library.
// Copyright 2010 Square, Inc.
// Licensed under the Apache License, Version 2.0.
// https://github.com/square/seismic
package io.sentry.android.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects shake gestures using the device's accelerometer.
 *
 * <p>The accelerometer sensor (TYPE_ACCELEROMETER) does NOT require any special permissions on
 * Android. The BODY_SENSORS permission is only needed for heart rate and similar body sensors.
 *
 * <p>Uses a rolling sample window: if more than 75% of accelerometer readings in the past 0.5s
 * exceed {@link #ACCELERATION_THRESHOLD}, a shake is detected. Based on Square's Seismic library.
 *
 * <p>Sensor events are delivered on a background {@link HandlerThread} to avoid polluting the main
 * thread.
 */
@ApiStatus.Internal
public final class SentryShakeDetector implements SensorEventListener {

  static final int ACCELERATION_THRESHOLD = 13;

  private @Nullable SensorManager sensorManager;
  private @Nullable Sensor accelerometer;
  private @Nullable HandlerThread handlerThread;
  private @Nullable Handler handler;
  private volatile @Nullable Listener listener;
  private @NotNull ILogger logger;

  private final @NotNull SampleQueue queue = new SampleQueue();

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
  void init(final @NotNull Context context, final @NotNull ILogger logger) {
    this.logger = logger;
    init(context);
  }

  private void init(final @NotNull Context context) {
    if (sensorManager == null) {
      sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }
    if (sensorManager != null && accelerometer == null) {
      accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false);
    }
    if (accelerometer != null && handlerThread == null) {
      handlerThread = new HandlerThread("sentry-shake");
      handlerThread.start();
      handler = new Handler(handlerThread.getLooper());
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
    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, handler);
  }

  public void stop() {
    listener = null;
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
    }
    final @Nullable Handler h = handler;
    if (h != null) {
      h.post(
          () -> {
            //noinspection Convert2MethodRef
            queue.clear();
          });
    }
  }

  /** Stops detection and releases the background thread. */
  public void close() {
    stop();
    if (handlerThread != null) {
      // quitSafely drains pending messages (including the clear posted by stop) before exiting
      handlerThread.quitSafely();
      handlerThread = null;
      handler = null;
    }
  }

  @Override
  public void onSensorChanged(final @NotNull SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
      return;
    }
    final float ax = event.values[0];
    final float ay = event.values[1];
    final float az = event.values[2];
    final boolean accelerating = Math.sqrt(ax * ax + ay * ay + az * az) > ACCELERATION_THRESHOLD;

    queue.add(event.timestamp, accelerating);
    if (queue.isShaking()) {
      queue.clear();
      final @Nullable Listener currentListener = listener;
      if (currentListener != null) {
        currentListener.onShake();
      }
    }
  }

  @Override
  public void onAccuracyChanged(final @NotNull Sensor sensor, final int accuracy) {
    // Not needed for shake detection.
  }

  static class SampleQueue {
    private static final long MAX_WINDOW_SIZE_NS = 500_000_000L; // 0.5s
    private static final long MIN_WINDOW_SIZE_NS = MAX_WINDOW_SIZE_NS >> 1; // 0.25s
    private static final int MIN_QUEUE_SIZE = 4;

    private final @NotNull SamplePool pool = new SamplePool();
    private @Nullable Sample oldest;
    private @Nullable Sample newest;
    private int sampleCount;
    private int acceleratingCount;

    void add(final long timestamp, final boolean accelerating) {
      purge(timestamp - MAX_WINDOW_SIZE_NS);

      final @NotNull Sample added = pool.acquire();
      added.timestamp = timestamp;
      added.accelerating = accelerating;
      added.next = null;
      if (newest != null) {
        newest.next = added;
      }
      newest = added;
      if (oldest == null) {
        oldest = added;
      }

      sampleCount++;
      if (accelerating) {
        acceleratingCount++;
      }
    }

    void clear() {
      while (oldest != null) {
        final @NotNull Sample removed = oldest;
        oldest = removed.next;
        pool.release(removed);
      }
      newest = null;
      sampleCount = 0;
      acceleratingCount = 0;
    }

    private void purge(final long cutoff) {
      while (sampleCount >= MIN_QUEUE_SIZE && oldest != null && cutoff - oldest.timestamp > 0) {
        final @NotNull Sample removed = oldest;
        if (removed.accelerating) {
          acceleratingCount--;
        }
        sampleCount--;
        oldest = removed.next;
        if (oldest == null) {
          newest = null;
        }
        pool.release(removed);
      }
    }

    boolean isShaking() {
      return newest != null
          && oldest != null
          && newest.timestamp - oldest.timestamp >= MIN_WINDOW_SIZE_NS
          && acceleratingCount >= (sampleCount >> 1) + (sampleCount >> 2);
    }
  }

  static class Sample {
    long timestamp;
    boolean accelerating;
    @Nullable Sample next;
  }

  static class SamplePool {
    private @Nullable Sample head;

    @NotNull
    Sample acquire() {
      Sample acquired = head;
      if (acquired == null) {
        acquired = new Sample();
      } else {
        head = acquired.next;
      }
      return acquired;
    }

    void release(final @NotNull Sample sample) {
      sample.next = head;
      head = sample;
    }
  }
}
