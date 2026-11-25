package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.os.Looper;
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
import io.sentry.android.core.hang.HangManager;
import io.sentry.android.core.hang.HangRecord;
import io.sentry.android.core.hang.HangRecordingListener;
import io.sentry.android.core.hang.HangRecordingRequest;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryThread;
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
 * Reports main-thread hangs that recover as non-fatal ANR events: the main thread runs again, or
 * the app goes to the background. A hang that does not recover, for example because the process is
 * killed, is left to the fatal ANR path.
 *
 * <p>Subscribes to the {@link HangManager} of the options for the SDK lifetime, and draws the
 * sample rate at each hang. Installed from Android 11 (API 30); below that, {@link AnrIntegration}
 * reports the same hangs.
 *
 * <p>Enable via {@link SentryAndroidOptions#setNonFatalAnrSampleRate(Double)} or the {@code
 * io.sentry.anr.non-fatal.sample-rate} manifest metadata.
 */
@ApiStatus.Internal
public final class NonFatalAnrIntegration implements Integration, Closeable {

  private final @NotNull AtomicReference<HangManager> subscribedManager = new AtomicReference<>();
  private final @NotNull AtomicReference<HangRecordingListener> subscription =
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
    final @Nullable HangManager manager = androidOptions.getHangManager();
    if (manager == null) {
      logger.log(SentryLevel.WARNING, "Non-fatal ANR is enabled but there is no hang manager");
      return;
    }
    if (!subscribedManager.compareAndSet(null, manager)) {
      return;
    }
    final @NotNull HangRecordingListener listener = new Reporter(scopes, androidOptions);
    subscription.set(listener);
    manager.subscribe(listener);
    addIntegrationToSdkVersion("NonFatalAnr");
  }

  @Override
  public void close() throws IOException {
    final @Nullable HangManager manager = subscribedManager.getAndSet(null);
    final @Nullable HangRecordingListener listener = subscription.getAndSet(null);
    if (manager != null && listener != null) {
      manager.unsubscribe(listener);
    }
  }

  private static final class Reporter implements HangRecordingListener {

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
    public @Nullable HangRecordingRequest onHangStarted() {
      final @Nullable Double sampleRate = options.getNonFatalAnrSampleRate();
      if (sampleRate != null && SentryRandom.current().nextDouble() < sampleRate) {
        return new HangRecordingRequest(true, false);
      }
      return null;
    }

    @Override
    public void onHangRecorded(final @NotNull HangRecord record) {
      if (record.getDurationMs() < options.getNonFatalAnrThresholdMs()) {
        return;
      }
      final long endTimestampMs = record.getStartTimestampMs() + record.getDurationMs();
      scopes.captureEvent(buildEvent(record, endTimestampMs));

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
      event.setThreads(Arrays.asList(mainThread(mainThreadId, culprit, exceptions)));

      final @NotNull Map<String, Object> context = new HashMap<>();
      context.put("duration_ms", record.getDurationMs());
      context.put("sample_count", samples.size());
      event.getContexts().put("non_fatal_anr", context);

      if (options.isEnableAnrFingerprinting()
          && AnrCulpritEventHelper.hasOnlySystemFrames(exceptions)) {
        event.setFingerprints(Arrays.asList("system-frames-only-anr", "non-fatal-anr"));
      } else {
        event.setFingerprints(Arrays.asList("{{ default }}", "non-fatal-anr"));
      }
      return event;
    }

    private static @NotNull SentryThread mainThread(
        final long mainThreadId,
        final @Nullable AggregatedStackTrace culprit,
        final @NotNull List<SentryException> exceptions) {
      final @NotNull SentryThread thread = new SentryThread();
      thread.setId(mainThreadId);
      thread.setName("main");
      thread.setCurrent(true);
      thread.setMain(true);
      if (culprit != null) {
        thread.setStacktrace(exceptions.get(0).getStacktrace());
      }
      return thread;
    }
  }
}
