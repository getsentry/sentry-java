package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.os.Looper;
import android.os.Process;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryExceptionFactory;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.android.core.anr.AggregatedStackTrace;
import io.sentry.android.core.anr.AnrCulpritEventHelper;
import io.sentry.android.core.anr.AnrCulpritIdentifier;
import io.sentry.android.core.anr.AnrStackTrace;
import io.sentry.android.core.anr.NonFatalAnrMarker;
import io.sentry.android.core.hang.HangInterval;
import io.sentry.android.core.hang.HangRecord;
import io.sentry.android.core.hang.HangRecorder;
import io.sentry.android.core.hang.HangRecorderListener;
import io.sentry.android.core.hang.HangRecordingRequest;
import io.sentry.android.core.hang.RuntimeStatsCollector;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.protocol.ArtContext;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryThread;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import io.sentry.util.SentryRandom;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports main-thread hangs as non-fatal ANR events: hangs that recover, before or after Android
 * shows the ANR dialog, and hangs that still block the main thread after 8 s, which are cut there.
 *
 * <p>If sampled, subscribes to the {@link HangRecorder} of the options for the SDK lifetime.
 * Installed from Android 11 (API 30), where {@link AnrV2Integration} skips the fatal ANRs that this
 * integration already reported.
 *
 * <p>Enable via {@link SentryAndroidOptions#setNonFatalAnrSampleRate(Double)} or the {@code
 * io.sentry.anr.non-fatal.sample-rate} manifest metadata.
 */
@ApiStatus.Internal
public final class NonFatalAnrIntegration implements Integration, Closeable {

  /**
   * Android reports an input ANR after 5 s at the earliest, so a shorter hang cannot be a fatal ANR
   * and gets no marker.
   */
  static final long MIN_FATAL_ANR_DURATION_MS = 5_000;

  private final @NotNull AtomicReference<HangRecorder> subscribedRecorder = new AtomicReference<>();
  private final @NotNull AtomicReference<HangRecorderListener> subscription =
      new AtomicReference<>();

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    final @NotNull SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");
    if (!androidOptions.isAnrEnabled() || !androidOptions.isNonFatalAnrEnabled()) {
      return;
    }
    final @NotNull ILogger logger = options.getLogger();
    final @Nullable HangRecorder recorder = androidOptions.getHangRecorder();
    if (recorder == null) {
      logger.log(SentryLevel.WARNING, "Non-fatal ANR is enabled but there is no hang recorder");
      return;
    }
    final @Nullable Double sampleRate = androidOptions.getNonFatalAnrSampleRate();
    if (sampleRate == null || SentryRandom.current().nextDouble() >= sampleRate) {
      logger.log(SentryLevel.DEBUG, "Non-fatal ANR is not sampled");
      return;
    }
    if (!subscribedRecorder.compareAndSet(null, recorder)) {
      return;
    }
    final @NotNull HangRecorderListener listener = new Reporter(scopes, androidOptions);
    subscription.set(listener);
    recorder.subscribe(listener);
    addIntegrationToSdkVersion("NonFatalAnr");
  }

  @Override
  public void close() throws IOException {
    final @Nullable HangRecorder recorder = subscribedRecorder.getAndSet(null);
    final @Nullable HangRecorderListener listener = subscription.getAndSet(null);
    if (recorder != null && listener != null) {
      recorder.unsubscribe(listener);
    }
  }

  private static final class Reporter implements HangRecorderListener {

    private final @NotNull IScopes scopes;
    private final @NotNull SentryAndroidOptions options;
    private final @NotNull AnrCulpritEventHelper culpritEventHelper;

    Reporter(final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
      this.scopes = scopes;
      this.options = options;
      this.culpritEventHelper =
          new AnrCulpritEventHelper(
              new SentryExceptionFactory(new SentryStackTraceFactory(options)));
    }

    @Override
    public @NotNull HangRecordingRequest onHangStarted(final @NotNull HangInterval interval) {
      return HangRecordingRequest.DEFAULT;
    }

    @Override
    public void onHangRecorded(final @NotNull HangRecord record) {
      if (record.getDurationMs() < options.getNonFatalAnrThresholdMs()) {
        return;
      }
      final long startTimestampMs = record.getStartTimestampMs();
      final long endTimestampMs = startTimestampMs + record.getDurationMs();

      // Only once the event is on disk: a kill before that must not suppress the fatal ANR.
      final @Nullable Runnable writeMarker;
      if (record.hasEndedAtMaxRecordingTime()
          || record.getDurationMs() >= MIN_FATAL_ANR_DURATION_MS) {
        final int pid = Process.myPid();
        writeMarker = () -> NonFatalAnrMarker.write(options, pid, startTimestampMs, endTimestampMs);
      } else {
        writeMarker = null;
      }

      final @NotNull SentryEvent event = buildEvent(record, endTimestampMs);
      scopes.captureEvent(
          event,
          HintUtils.createWithTypeCheckHint(
              new NonFatalAnrHint(record.hasEndedAtMaxRecordingTime(), writeMarker)));

      final @NotNull Breadcrumb breadcrumb = new Breadcrumb();
      breadcrumb.setCategory("anr");
      breadcrumb.setType("error");
      breadcrumb.setLevel(SentryLevel.WARNING);
      breadcrumb.setMessage("Non-fatal ANR");
      breadcrumb.setData("duration_ms", record.getDurationMs());
      scopes.addBreadcrumb(breadcrumb);
    }

    private @NotNull SentryEvent buildEvent(
        final @NotNull HangRecord record, final long endTimestampMs) {
      final @NotNull List<AnrStackTrace> samples = record.getStackTraces();
      final @Nullable AggregatedStackTrace culprit =
          samples.isEmpty() ? null : AnrCulpritIdentifier.identify(samples);

      final @NotNull Mechanism mechanism = new Mechanism();
      mechanism.setType("NonFatalANR");
      mechanism.setHandled(true);

      @Nullable
      List<SentryException> exceptions =
          culprit == null ? null : culpritEventHelper.toSentryExceptions(culprit, mechanism);
      if (exceptions == null) {
        final @NotNull SentryException exception = new SentryException();
        exception.setType(ApplicationNotResponding.class.getName());
        exception.setValue("Non-fatal ANR");
        exception.setMechanism(mechanism);
        exceptions = Arrays.asList(exception);
      }
      final long mainThreadId =
          AndroidThreadChecker.getThreadId(Looper.getMainLooper().getThread());
      for (final @NotNull SentryException exception : exceptions) {
        exception.setThreadId(mainThreadId);
      }

      final @NotNull SentryEvent event = new SentryEvent(DateUtils.getDateTime(endTimestampMs));
      event.setLevel(SentryLevel.ERROR);
      event.setExceptions(exceptions);
      event.setThreads(Arrays.asList(mainThread(mainThreadId, samples, culprit, exceptions)));

      final @NotNull Map<String, Object> context = new HashMap<>();
      context.put("duration_ms", record.getDurationMs());
      context.put("sample_count", samples.size());
      final @Nullable Map<String, Object> runtimeStats = record.getRuntimeStats();
      if (runtimeStats != null) {
        putDelta(context, runtimeStats, RuntimeStatsCollector.KEY_GC_COUNT_DELTA);
        putDelta(context, runtimeStats, RuntimeStatsCollector.KEY_GC_TIME_MS_DELTA);
        putDelta(context, runtimeStats, RuntimeStatsCollector.KEY_BLOCKING_GC_COUNT_DELTA);
        putDelta(context, runtimeStats, RuntimeStatsCollector.KEY_BLOCKING_GC_TIME_MS_DELTA);
        event.getContexts().setArt(toArtContext(runtimeStats));
      }
      event.getContexts().put("non_fatal_anr", context);

      if (event.getFingerprints() == null) {
        if (options.isEnableAnrFingerprinting()
            && AnrCulpritEventHelper.hasOnlySystemFrames(exceptions)) {
          event.setFingerprints(Arrays.asList("system-frames-only-anr", "non-fatal-anr"));
        } else {
          event.setFingerprints(Arrays.asList("{{ default }}", "non-fatal-anr"));
        }
      }
      return event;
    }

    private static void putDelta(
        final @NotNull Map<String, Object> context,
        final @NotNull Map<String, Object> runtimeStats,
        final @NotNull String key) {
      final @Nullable Object value = runtimeStats.get(key);
      if (value != null) {
        context.put(key, value);
      }
    }

    private static @NotNull ArtContext toArtContext(final @NotNull Map<String, Object> stats) {
      final @NotNull ArtContext art = new ArtContext();
      art.setGcTotalCount(asLong(stats.get(RuntimeStatsCollector.KEY_GC_COUNT)));
      art.setGcTotalTime(asDouble(stats.get(RuntimeStatsCollector.KEY_GC_TIME_MS)));
      art.setGcBlockingCount(asLong(stats.get(RuntimeStatsCollector.KEY_BLOCKING_GC_COUNT)));
      art.setGcBlockingTime(asDouble(stats.get(RuntimeStatsCollector.KEY_BLOCKING_GC_TIME_MS)));
      art.setGcPreOomeCount(asLong(stats.get(RuntimeStatsCollector.KEY_PRE_OOME_GC_COUNT)));
      art.setGcWaitingTime(asDouble(stats.get(RuntimeStatsCollector.KEY_GC_WAITING_TIME_MS)));
      art.setFreeMemory(asLong(stats.get(RuntimeStatsCollector.KEY_FREE_MEMORY)));
      art.setFreeMemoryUntilOome(
          asLong(stats.get(RuntimeStatsCollector.KEY_FREE_MEMORY_UNTIL_OOME)));
      art.setTotalMemory(asLong(stats.get(RuntimeStatsCollector.KEY_TOTAL_MEMORY)));
      art.setMaxMemory(asLong(stats.get(RuntimeStatsCollector.KEY_MAX_MEMORY)));
      return art;
    }

    private static @Nullable Long asLong(final @Nullable Object value) {
      return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static @Nullable Double asDouble(final @Nullable Object value) {
      return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static @NotNull SentryThread mainThread(
        final long mainThreadId,
        final @NotNull List<AnrStackTrace> samples,
        final @Nullable AggregatedStackTrace culprit,
        final @NotNull List<SentryException> exceptions) {
      final @NotNull SentryThread thread = new SentryThread();
      thread.setId(mainThreadId);
      thread.setName("main");
      thread.setCurrent(true);
      thread.setMain(true);
      final @Nullable Thread.State state = culpritState(samples, culprit);
      if (state != null) {
        thread.setState(state.name());
      }
      if (culprit != null) {
        thread.setStacktrace(exceptions.get(0).getStacktrace());
      }
      return thread;
    }

    private static @Nullable Thread.State culpritState(
        final @NotNull List<AnrStackTrace> samples, final @Nullable AggregatedStackTrace culprit) {
      if (samples.isEmpty()) {
        return null;
      }
      if (culprit != null) {
        final @NotNull StackTraceElement[] culpritStack = culprit.getStack();
        for (int i = samples.size() - 1; i >= 0; i--) {
          if (containsRun(samples.get(i).stack, culpritStack)) {
            return samples.get(i).state;
          }
        }
      }
      return samples.get(samples.size() - 1).state;
    }

    private static boolean containsRun(
        final @NotNull StackTraceElement[] stack, final @NotNull StackTraceElement[] run) {
      for (int start = 0; start + run.length <= stack.length; start++) {
        int offset = 0;
        while (offset < run.length && stack[start + offset].equals(run[offset])) {
          offset++;
        }
        if (offset == run.length) {
          return true;
        }
      }
      return false;
    }
  }
}
