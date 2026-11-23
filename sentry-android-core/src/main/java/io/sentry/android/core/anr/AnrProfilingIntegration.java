package io.sentry.android.core.anr;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.AppState;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.hang.HangRecord;
import io.sentry.android.core.hang.HangRecorder;
import io.sentry.android.core.hang.HangRecorderListener;
import io.sentry.android.core.hang.HangRecordingRequest;
import io.sentry.util.Objects;
import io.sentry.util.SentryRandom;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Captures main-thread stack profiles while the app is unresponsive and attaches them to ANR events
 * on the next app start.
 *
 * <p>Subscribes to the {@link HangRecorder} of the options for the SDK lifetime, and draws the
 * sample rate at each hang. The recorder writes the stacks of a sampled hang to disk. An unsampled
 * hang needs nothing from the recorder.
 *
 * <p>Enable via {@link SentryAndroidOptions#setAnrProfilingSampleRate(Double)} or the {@code
 * io.sentry.anr.profiling.sample-rate} manifest metadata.
 */
public final class AnrProfilingIntegration
    implements Integration, Closeable, AppState.AppStateListener, Runnable {

  // StackTraceConverter expects the samples this far apart.
  public static final long POLLING_INTERVAL_MS = HangRecorder.DEFAULT_POLLING_INTERVAL_MS;
  public static final long THRESHOLD_ANR_MS = 4000;

  private final @NotNull AtomicReference<HangRecorder> subscribedRecorder = new AtomicReference<>();
  private final @NotNull AtomicReference<HangRecorderListener> subscription =
      new AtomicReference<>();

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    final @NotNull SentryAndroidOptions androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");
    if (!androidOptions.isAnrProfilingEnabled()) {
      return;
    }
    final @NotNull ILogger logger = options.getLogger();
    if (androidOptions.getCacheDirPath() == null) {
      logger.log(SentryLevel.WARNING, "ANR Profiling is enabled but cacheDirPath is not set");
      return;
    }
    final @Nullable HangRecorder recorder = androidOptions.getHangRecorder();
    if (recorder == null) {
      logger.log(SentryLevel.WARNING, "ANR Profiling is enabled but there is no hang recorder");
      return;
    }
    if (!subscribedRecorder.compareAndSet(null, recorder)) {
      return;
    }
    final @NotNull HangRecorderListener listener = new ProfileSampler(androidOptions);
    subscription.set(listener);
    recorder.subscribe(listener);
    addIntegrationToSdkVersion("AnrProfiling");
  }

  @Override
  public void close() throws IOException {
    final @Nullable HangRecorder recorder = subscribedRecorder.getAndSet(null);
    final @Nullable HangRecorderListener listener = subscription.getAndSet(null);
    if (recorder != null && listener != null) {
      recorder.unsubscribe(listener);
    }
  }

  /**
   * @deprecated Not used by the SDK any more.
   */
  @Deprecated
  @Override
  public void onForeground() {}

  /**
   * @deprecated Not used by the SDK any more.
   */
  @Deprecated
  @Override
  public void onBackground() {}

  /**
   * @deprecated Not used by the SDK any more.
   */
  @Deprecated
  @Override
  public void run() {}

  private static final class ProfileSampler implements HangRecorderListener {

    private final @NotNull SentryAndroidOptions options;

    ProfileSampler(final @NotNull SentryAndroidOptions options) {
      this.options = options;
    }

    @Override
    public @Nullable HangRecordingRequest onHangStarted() {
      final @Nullable Double sampleRate = options.getAnrProfilingSampleRate();
      if (sampleRate != null && SentryRandom.current().nextDouble() < sampleRate) {
        return new HangRecordingRequest(true, true);
      }
      return null;
    }

    @Override
    public void onHangRecorded(final @NotNull HangRecord record) {
      // The fatal path reads the profile from disk at the next launch, it needs no record.
    }
  }
}
