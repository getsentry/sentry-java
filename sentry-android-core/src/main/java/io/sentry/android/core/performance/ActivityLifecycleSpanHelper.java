package io.sentry.android.core.performance;

import android.os.Looper;
import android.os.SystemClock;
import io.sentry.ISpan;
import io.sentry.Instrumenter;
import io.sentry.SentryDate;
import io.sentry.SpanDataConvention;
import io.sentry.SpanStatus;
import io.sentry.android.core.AndroidDateUtils;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class ActivityLifecycleSpanHelper {
  private static final String APP_METRICS_ACTIVITIES_OP = "activity.load";

  private final @NotNull String activityName;

  private @Nullable SentryDate onCreateStartTimestamp = null;
  private @Nullable SentryDate onStartStartTimestamp = null;
  private @Nullable ISpan onCreateSpan = null;
  private @Nullable ISpan onStartSpan = null;

  public ActivityLifecycleSpanHelper(final @NotNull String activityName) {
    this.activityName = activityName;
  }

  public void setOnCreateStartTimestamp(final @NotNull SentryDate onCreateStartTimestamp) {
    this.onCreateStartTimestamp = onCreateStartTimestamp;
  }

  public void setOnStartStartTimestamp(final @NotNull SentryDate onStartStartTimestamp) {
    this.onStartStartTimestamp = onStartStartTimestamp;
  }

  public void createAndStopOnCreateSpan(final @Nullable ISpan parentSpan) {
    if (onCreateStartTimestamp != null && parentSpan != null) {
      onCreateSpan =
          createLifecycleSpan(parentSpan, activityName + ".onCreate", onCreateStartTimestamp);
      onCreateSpan.finish();
    }
  }

  public void createAndStopOnStartSpan(final @Nullable ISpan parentSpan) {
    if (onStartStartTimestamp != null && parentSpan != null) {
      onStartSpan =
          createLifecycleSpan(parentSpan, activityName + ".onStart", onStartStartTimestamp);
      onStartSpan.finish();
    }
  }

  public @Nullable ISpan getOnCreateSpan() {
    return onCreateSpan;
  }

  public @Nullable ISpan getOnStartSpan() {
    return onStartSpan;
  }

  public @Nullable SentryDate getOnCreateStartTimestamp() {
    return onCreateStartTimestamp;
  }

  public @Nullable SentryDate getOnStartStartTimestamp() {
    return onStartStartTimestamp;
  }

  public void saveSpanToAppStartMetrics() {
    if (onCreateSpan == null || onStartSpan == null) {
      return;
    }
    final @Nullable SentryDate onCreateFinishDate = onCreateSpan.getFinishDate();
    final @Nullable SentryDate onStartFinishDate = onStartSpan.getFinishDate();
    if (onCreateFinishDate == null || onStartFinishDate == null) {
      return;
    }
    final long now = SystemClock.uptimeMillis();
    final @NotNull SentryDate nowDate = AndroidDateUtils.getCurrentSentryDateTime();
    final long onCreateShiftMs =
        TimeUnit.NANOSECONDS.toMillis(nowDate.diff(onCreateSpan.getStartDate()));
    final long onCreateStopShiftMs =
        TimeUnit.NANOSECONDS.toMillis(nowDate.diff(onCreateFinishDate));
    final long onStartShiftMs =
        TimeUnit.NANOSECONDS.toMillis(nowDate.diff(onStartSpan.getStartDate()));
    final long onStartStopShiftMs = TimeUnit.NANOSECONDS.toMillis(nowDate.diff(onStartFinishDate));

    ActivityLifecycleTimeSpan activityLifecycleTimeSpan = new ActivityLifecycleTimeSpan();
    activityLifecycleTimeSpan
        .getOnCreate()
        .setup(
            onCreateSpan.getDescription(),
            TimeUnit.NANOSECONDS.toMillis(onCreateSpan.getStartDate().nanoTimestamp()),
            now - onCreateShiftMs,
            now - onCreateStopShiftMs);
    activityLifecycleTimeSpan
        .getOnStart()
        .setup(
            onStartSpan.getDescription(),
            TimeUnit.NANOSECONDS.toMillis(onStartSpan.getStartDate().nanoTimestamp()),
            now - onStartShiftMs,
            now - onStartStopShiftMs);
    AppStartMetrics.getInstance().addActivityLifecycleTimeSpans(activityLifecycleTimeSpan);
  }

  private @NotNull ISpan createLifecycleSpan(
      final @NotNull ISpan parentSpan,
      final @NotNull String description,
      final @NotNull SentryDate startTimestamp) {
    final @NotNull ISpan span =
        parentSpan.startChild(
            APP_METRICS_ACTIVITIES_OP, description, startTimestamp, Instrumenter.SENTRY);
    setDefaultStartSpanData(span);
    return span;
  }

  public void clear() {
    // in case the parentSpan isn't completed yet, we finish it as cancelled to avoid memory leak
    if (onCreateSpan != null && !onCreateSpan.isFinished()) {
      onCreateSpan.finish(SpanStatus.CANCELLED);
    }
    onCreateSpan = null;
    if (onStartSpan != null && !onStartSpan.isFinished()) {
      onStartSpan.finish(SpanStatus.CANCELLED);
    }
    onStartSpan = null;
  }

  private void setDefaultStartSpanData(final @NotNull ISpan span) {
    span.setData(SpanDataConvention.THREAD_ID, Looper.getMainLooper().getThread().getId());
    span.setData(SpanDataConvention.THREAD_NAME, "main");
    span.setData(SpanDataConvention.CONTRIBUTES_TTID, true);
    span.setData(SpanDataConvention.CONTRIBUTES_TTFD, true);
  }
}
