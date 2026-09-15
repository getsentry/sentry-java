package io.sentry.android.core;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.NoOpLogger;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.hints.PreviousSessionAbnormalExit;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports Android process deaths that the OS records as <a
 * href="https://source.android.com/docs/core/perf/memory-limiter">MemoryLimiter</a> kills.
 *
 * <p>Checks Android's historical {@link ApplicationExitInfo} records on app start, finds exits that
 * match the MemoryLimiter signature, and turns them into Sentry events.
 *
 * <p><b>Data generated</b>
 *
 * <p>Each matching exit is reported as a synthetic fatal event with a {@code MemoryLimitExceeded}
 * exception. The original Android exit description is stored together with MemoryLimiter-specific
 * context in {@code mechanism.data}, including the raw {@link ApplicationExitInfo#getImportance()}
 * value and a derived {@code process_visibility} classification ({@code visible}, {@code
 * not_visible}, or {@code cached}).
 *
 * <p>The process-visibility value is a best-effort mapping of Android's <a
 * href="https://source.android.com/docs/core/perf/memory-limiter#process-monitoring">MemoryLimiter
 * process-monitoring states</a>. {@link ApplicationExitInfo} exposes process importance but not the
 * exact {@code PROCESS_STATE_*} value used by that table, so some importance bands remain
 * inherently lossy when translated back into MemoryLimiter's visibility categories.
 *
 * <p>Events may also be backfilled with persisted launch state from the crashed app generation,
 * including release info, environment, and other scope data.
 *
 * <p><b>Limitations</b>
 *
 * <p>Only available on Android API ≥ 37.
 */
@ApiStatus.Internal
public final class MemoryLimiterIntegration implements Integration, Closeable {

  static final @NotNull String MEMORY_LIMITER_DESCRIPTION_PREFIX = "MemoryLimiter:";
  static final @NotNull String MEMORY_LIMITER_DESCRIPTION =
      MEMORY_LIMITER_DESCRIPTION_PREFIX + "AnonSwap";

  static final @NotNull String MEMORY_LIMITER_MESSAGE = "Android process killed by MemoryLimiter";
  static final @NotNull String MEMORY_LIMITER_MECHANISM = "memory_limiter";

  static final @NotNull String IMPORTANCE_DATA_KEY = "importance";
  static final @NotNull String PROCESS_VISIBILITY_DATA_KEY = "process_visibility";
  static final @NotNull String PROCESS_VISIBILITY_CACHED = "cached";
  static final @NotNull String PROCESS_VISIBILITY_NOT_VISIBLE = "not_visible";
  static final @NotNull String PROCESS_VISIBILITY_VISIBLE = "visible";

  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private @Nullable SentryAndroidOptions androidOptions;

  public MemoryLimiterIntegration(final @NotNull Context context) {
    // Use CurrentDateProvider instead of AndroidCurrentDateProvider as ApplicationExitInfo uses
    // epochal System.currentTimeMillis and not time since boot.
    this(
        context,
        CurrentDateProvider.getInstance(),
        new BuildInfoProvider(NoOpLogger.getInstance()));
  }

  MemoryLimiterIntegration(
      final @NotNull Context context,
      final @NotNull ICurrentDateProvider dateProvider,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
    this.buildInfoProvider = buildInfoProvider;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    androidOptions =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    androidOptions
        .getLogger()
        .log(
            DEBUG, "MemoryLimiterIntegration enabled: %s", androidOptions.isMemoryLimiterEnabled());

    if (!androidOptions.isMemoryLimiterEnabled()) {
      return;
    }

    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.CINNAMON_BUN) {
      androidOptions
          .getLogger()
          .log(
              INFO,
              "MemoryLimiter is only supported on Android API 37 and above. Skipping registration.");
      return;
    }

    if (this.androidOptions.getCacheDirPath() == null) {
      this.androidOptions
          .getLogger()
          .log(
              INFO,
              "Cache dir is not set, unable to process MemoryLimiter exits. Skipping registration.");
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              new ApplicationExitInfoHistoryDispatcher(
                  context,
                  scopes,
                  androidOptions,
                  dateProvider,
                  new MemoryLimiterPolicy(androidOptions)));
    } catch (Throwable e) {
      ExceptionUtils.rethrowIfFatal(e);
      options.getLogger().log(DEBUG, "Failed to start MemoryLimiter processor.", e);
    }

    options.getLogger().log(DEBUG, "MemoryLimiterIntegration installed.");
    addIntegrationToSdkVersion("MemoryLimiter");
  }

  @Override
  public void close() throws IOException {
    if (androidOptions != null) {
      androidOptions.getLogger().log(DEBUG, "MemoryLimiterIntegration removed.");
    }
  }

  /**
   * Defines how MemoryLimiter exits are recognized and reported within the shared {@link
   * ApplicationExitInfoHistoryDispatcher} pipeline.
   *
   * <p>This policy provides the MemoryLimiter-specific rules for that pipeline, including which
   * exit records match, whether older matching exits should also be reported, how deduplication is
   * tracked on disk, and what synthetic event and hint should be created for a matching exit.
   */
  private static final class MemoryLimiterPolicy implements ApplicationExitInfoPolicy {

    private final @NotNull SentryAndroidOptions options;

    private MemoryLimiterPolicy(final @NotNull SentryAndroidOptions options) {
      this.options = options;
    }

    @Override
    public @NotNull String getLabel() {
      return "MemoryLimiter process death";
    }

    /**
     * Returns true if the provided {@code exitInfo} looks like it comes from a
     * MemoryLimiter-induced process death.
     *
     * <p>Criteria taken from <a
     * href="https://developer.android.com/about/versions/17/behavior-changes-all#app-memory-limits">here</a>.
     */
    @Override
    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean matches(final @NotNull ApplicationExitInfo exitInfo) {
      if (exitInfo.getReason() != ApplicationExitInfo.REASON_OTHER) {
        return false;
      }

      final String description = exitInfo.getDescription();
      // We match on the "MemoryLimiter:" prefix rather than the full "MemoryLimiter:AnonSwap"
      // string mentioned in the Android 17 release notes because we want to capture any future
      // MemoryLimiter kill reason without a code change. (MemoryLimiter source already tracks
      // MemoryLimiter:Memory and MemoryLimiter:Swap reasons, but for now doesn't kill the process
      // because of them.)
      return description != null && description.contains(MEMORY_LIMITER_DESCRIPTION_PREFIX);
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalMemoryLimiterExits();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedMemoryLimiter(options);
    }

    @Override
    public void markReported(final long timestamp) {
      AndroidEnvelopeCache.markMemoryLimiterReported(options, timestamp);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.R)
    public @NotNull ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final long timestamp = exitInfo.getTimestamp();

      final MemoryLimiterHint memoryLimiterHint =
          new MemoryLimiterHint(
              options.getFlushTimeoutMillis(), options.getLogger(), timestamp, shouldEnrich);
      final Hint hint = HintUtils.createWithTypeCheckHint(memoryLimiterHint);

      final Message message = new Message();
      message.setFormatted(MEMORY_LIMITER_MESSAGE);

      final SentryEvent event = new SentryEvent();
      event.setMessage(message);
      event.setLevel(SentryLevel.FATAL);
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
      event.setTimestamp(DateUtils.getDateTime(timestamp));
      event.setExceptions(Collections.singletonList(buildException(exitInfo, shouldEnrich)));

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, memoryLimiterHint);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private @NotNull SentryException buildException(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(shouldEnrich ? "AppExitInfo" : "HistoricalAppExitInfo");
      mechanism.setDescription(exitInfo.getDescription());
      mechanism.setHandled(false);
      mechanism.setSynthetic(true);
      mechanism.setData(buildMechanismData(exitInfo));

      final SentryException sentryException = new SentryException();
      sentryException.setType("MemoryLimitExceeded");
      sentryException.setValue(MEMORY_LIMITER_MESSAGE);
      sentryException.setModule("io.sentry.android.core");
      sentryException.setMechanism(mechanism);
      return sentryException;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private @NotNull Map<String, Object> buildMechanismData(
        final @NotNull ApplicationExitInfo exitInfo) {
      final int importance = exitInfo.getImportance();
      final Map<String, Object> data = new HashMap<>();
      data.put(IMPORTANCE_DATA_KEY, importance);
      data.put(PROCESS_VISIBILITY_DATA_KEY, getProcessVisibility(importance));
      return data;
    }

    /**
     * Best-effort mapping from {@link ApplicationExitInfo#getImportance()} to MemoryLimiter's
     * visible / not-visible / cached categories.
     *
     * <p>The <a
     * href="https://source.android.com/docs/core/perf/memory-limiter#process-monitoring">MemoryLimiter
     * docs</a> classify exact {@code PROCESS_STATE_*} values, but {@link ApplicationExitInfo} only
     * exposes the coarser {@link RunningAppProcessInfo} importance bucket.
     */
    private @NotNull String getProcessVisibility(final int importance) {
      switch (importance) {
        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
        case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
        case RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
          return PROCESS_VISIBILITY_VISIBLE;

        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
        case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
        case RunningAppProcessInfo.IMPORTANCE_SERVICE:
        case RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
        case RunningAppProcessInfo.IMPORTANCE_GONE:
          return PROCESS_VISIBILITY_NOT_VISIBLE;

        case RunningAppProcessInfo.IMPORTANCE_CACHED:
        default:
          // Fall back to the least specific bucket.
          return PROCESS_VISIBILITY_CACHED;
      }
    }
  }

  /**
   * Hint associated with a recovered MemoryLimiter event.
   *
   * <p>This hint serves two purposes in the {@link ApplicationExitInfo} recovery pipeline: it lets
   * the dispatcher wait for the event to flush to disk before considering the exit reported, and it
   * tells {@link ApplicationExitInfoEventProcessor} whether the recovered event should be
   * backfilled with persisted launch state or kept as a lighter historical record.
   */
  @ApiStatus.Internal
  public static final class MemoryLimiterHint extends BlockingFlushHint
      implements PreviousSessionAbnormalExit {

    private final long timestamp;
    private final boolean shouldEnrich;

    public MemoryLimiterHint(
        final long flushTimeoutMillis,
        final @NotNull ILogger logger,
        final long timestamp,
        final boolean shouldEnrich) {
      super(flushTimeoutMillis, logger);
      this.timestamp = timestamp;
      this.shouldEnrich = shouldEnrich;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return timestamp;
    }

    @Override
    public @NotNull String mechanism() {
      return MEMORY_LIMITER_MECHANISM;
    }

    @Override
    public boolean shouldEnrich() {
      return shouldEnrich;
    }

    @Override
    public boolean shouldUpdatePreviousSession() {
      return shouldEnrich;
    }

    @Override
    public boolean isFlushable(@Nullable SentryId eventId) {
      return true;
    }

    @Override
    public void setFlushable(@NotNull SentryId eventId) {}
  }
}
