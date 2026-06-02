package io.sentry.samples.android;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;
import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import java.util.Map;

/** Apps. main Application. */
public class MyApplication extends Application {

  private static final String E2E_TAG = "SentryE2E";

  @Override
  public void onCreate() {
    // Mirrors the Gradle plugin's start/end hooks; missing the start hook produces invalid
    // application.load timestamps.
    if (BuildConfig.SIMULATE_GRADLE_PLUGIN) {
      AppStartMetrics.onApplicationCreate(this);
    }

    // beforeSendTransaction observes the transaction after PerformanceAndroidEventProcessor.
    SentryAndroid.init(
        this,
        options -> {
          options.setBeforeSendTransaction(
              (txn, hint) -> {
                logTransaction(txn);
                return txn;
              });
        });

    Sentry.startProfiler();
    strictMode();
    super.onCreate();

    if (BuildConfig.SIMULATE_GRADLE_PLUGIN) {
      Log.d(E2E_TAG, "SIMULATE_GRADLE_PLUGIN=true -> calling onApplicationPostCreate");
      AppStartMetrics.onApplicationPostCreate(this);
    }

    Log.d(E2E_TAG, "APP_ONCREATE_DONE");
  }

  private static void logTransaction(SentryTransaction txn) {
    final String name = txn.getTransaction();
    final String eventId = txn.getEventId() != null ? txn.getEventId().toString() : "null";
    String op = "null";
    String traceId = "null";
    String rootSpanId = "null";
    if (txn.getContexts() != null && txn.getContexts().getTrace() != null) {
      op = txn.getContexts().getTrace().getOperation();
      if (txn.getContexts().getTrace().getTraceId() != null) {
        traceId = txn.getContexts().getTrace().getTraceId().toString();
      }
      if (txn.getContexts().getTrace().getSpanId() != null) {
        rootSpanId = txn.getContexts().getTrace().getSpanId().toString();
      }
    }

    final StringBuilder measurements = new StringBuilder("[");
    if (txn.getMeasurements() != null) {
      boolean first = true;
      for (Map.Entry<String, io.sentry.protocol.MeasurementValue> e :
          txn.getMeasurements().entrySet()) {
        if (!first) measurements.append(",");
        measurements.append(e.getKey()).append("=").append(e.getValue().getValue());
        first = false;
      }
    }
    measurements.append("]");

    final StringBuilder children = new StringBuilder("[");
    if (txn.getSpans() != null) {
      boolean first = true;
      for (SentrySpan s : txn.getSpans()) {
        if (!first) children.append(",");
        final String parentInfo =
            s.getParentSpanId() == null
                ? "orphan"
                : (s.getParentSpanId().toString().equals(rootSpanId) ? "root" : "nested");
        children.append(s.getOp()).append("(").append(parentInfo).append(")");
        first = false;
      }
    }
    children.append("]");

    Log.d(
        E2E_TAG,
        "TXN|name="
            + name
            + "|op="
            + op
            + "|eventId="
            + eventId
            + "|traceId="
            + traceId
            + "|rootSpanId="
            + rootSpanId
            + "|measurements="
            + measurements
            + "|children="
            + children);
  }

  private void strictMode() {
    //    https://developer.android.com/reference/android/os/StrictMode
    //    StrictMode is a developer tool which detects things you might be doing by accident and
    //    brings them to your attention so you can fix them.
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }
  }
}
