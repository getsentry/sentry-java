package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Sentry;
import io.sentry.SpanContext;
import io.sentry.SpanId;
import io.sentry.SpanStatus;
import io.sentry.TracesSamplingDecision;
import io.sentry.android.core.internal.gestures.NoOpWindowCallback;
import io.sentry.android.core.performance.ActivityLifecycleCallbacksAdapter;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.NextDrawListener;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.android.core.performance.WindowContentChangedCallback;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryPerformanceProvider extends EmptySecureContentProvider {

  private static final long MAX_APP_START_DURATION_MS = 10000;

  @Override
  public boolean onCreate() {
    onAppLaunched();
    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public String getType(@NotNull Uri uri) {
    return null;
  }

  private void onAppLaunched() {
    // Process.getStartUptimeMillis() requires N
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
      return;
    }
    final @Nullable Application app = (Application) getContext();
    if (app == null) {
      return;
    }

    final TimeSpan appStartTimespan = AppStartMetrics.getInstance().getAppStartTimespan();
    appStartTimespan.setStartedAt(Process.getStartUptimeMillis());

    final AtomicBoolean firstDrawDone = new AtomicBoolean(false);
    final Handler handler = new Handler(Looper.getMainLooper());
    final WeakHashMap<Activity, TimeSpan> activityTimeSpans = new WeakHashMap<>();

    app.registerActivityLifecycleCallbacks(
        new ActivityLifecycleCallbacksAdapter() {
          @Override
          public void onActivityPreCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            final TimeSpan timeSpan = new TimeSpan();
            timeSpan.start();
            activityTimeSpans.put(activity, timeSpan);
          }

          @Override
          public void onActivityPostCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            final @Nullable TimeSpan timeSpan = activityTimeSpans.get(activity);
            if (timeSpan != null) {
              timeSpan.stop();
              timeSpan.setDescription(activity.getClass().getName());
            }
          }
        });

    app.registerActivityLifecycleCallbacks(
        new ActivityLifecycleCallbacksAdapter() {
          @Override
          public void onActivityCreated(
              @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (firstDrawDone.get()) {
              return;
            }
            @Nullable Window window = activity.getWindow();
            if (window != null) {
              @Nullable View decorView = window.peekDecorView();
              if (decorView != null) {
                new NextDrawListener(
                        decorView,
                        () -> {
                          handler.postAtFrontOfQueue(
                              () -> {
                                appStartTimespan.stop();
                                firstDrawDone.set(true);
                                onAppStartDone();
                              });
                        })
                    .safelyRegisterForNextDraw();
              } else {
                @Nullable Window.Callback oldCallback = window.getCallback();
                if (oldCallback == null) {
                  oldCallback = new NoOpWindowCallback();
                }
                window.setCallback(
                    new WindowContentChangedCallback(
                        oldCallback,
                        () -> {
                          @Nullable View newDecorView = window.peekDecorView();
                          if (newDecorView != null) {
                            new NextDrawListener(
                                    newDecorView,
                                    () -> {
                                      handler.postAtFrontOfQueue(
                                          () -> {
                                            appStartTimespan.stop();
                                            firstDrawDone.set(true);
                                            onAppStartDone();
                                          });
                                    })
                                .safelyRegisterForNextDraw();
                          }
                        }));
              }
            }
          }
        });
  }

  private synchronized void onAppStartDone() {
    final @NotNull IHub hub = Sentry.getCurrentHub();
    final @NotNull AppStartMetrics metrics = AppStartMetrics.getInstance();

    if (metrics.getAppStartTimespan().hasNotStopped()) {
      // discarding invalid measurement
      return;
    }

    if (metrics.getAppStartTimespan().getDurationMs() > MAX_APP_START_DURATION_MS) {
      return;
    }

    // TODO how to determine tracing sampling decision
    final SpanContext rootContext =
        new SpanContext("app.start.cold", new TracesSamplingDecision(true));
    final List<SentrySpan> appStartSpans = new ArrayList<>();
    final TransactionInfo transactionInfo = new TransactionInfo("auto.performance");

    final List<TimeSpan> contentProviderTimeSpans = metrics.getContentProviderOnCreateTimeSpans();
    if (!contentProviderTimeSpans.isEmpty()) {
      final SentrySpan contentProviderRootSpan =
          new SentrySpan(
              contentProviderTimeSpans.get(0).getStartTimestampS(),
              contentProviderTimeSpans
                  .get(contentProviderTimeSpans.size() - 1)
                  .getProjectedStopTimestampS(),
              rootContext.getTraceId(),
              new SpanId(),
              rootContext.getSpanId(),
              "ui.load",
              "ContentProvider",
              SpanStatus.OK,
              null,
              Collections.emptyMap(),
              null);

      appStartSpans.add(contentProviderRootSpan);
      for (TimeSpan timeSpan : contentProviderTimeSpans) {
        appStartSpans.add(
            new SentrySpan(
                timeSpan.getStartTimestampS(),
                timeSpan.getProjectedStopTimestampS(),
                rootContext.getTraceId(),
                new SpanId(),
                contentProviderRootSpan.getSpanId(),
                "ui.load",
                timeSpan.getDescription(),
                SpanStatus.OK,
                null,
                Collections.emptyMap(),
                null));
      }
    }

    final TimeSpan applicationOnCreateTimeSpan = metrics.getApplicationOnCreateTimeSpan();
    if (applicationOnCreateTimeSpan.hasStopped()) {
      appStartSpans.add(
          new SentrySpan(
              applicationOnCreateTimeSpan.getStartTimestampS(),
              applicationOnCreateTimeSpan.getProjectedStopTimestampS(),
              rootContext.getTraceId(),
              new SpanId(),
              rootContext.getSpanId(),
              "ui.load",
              applicationOnCreateTimeSpan.getDescription(),
              SpanStatus.OK,
              null,
              Collections.emptyMap(),
              null));
    }

    final SentryTransaction transaction =
        new SentryTransaction(
            "app.start.cold",
            metrics.getAppStartTimespan().getStartTimestampS(),
            metrics.getAppStartTimespan().getProjectedStopTimestampS(),
            appStartSpans,
            Collections.emptyMap(),
            transactionInfo);
    transaction.getContexts().setTrace(rootContext);

    // TODO maybe use an observer pattern instead and notift
    final Hint hint = new Hint();
    hub.captureTransaction(transaction, hint);
    metrics.clear();
  }
}
