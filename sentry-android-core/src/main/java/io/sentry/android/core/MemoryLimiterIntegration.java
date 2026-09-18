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
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Backfillable;
import io.sentry.hints.BlockingFlushHint;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Reports Android process deaths that the OS records as <a
 * href="https://source.android.com/docs/core/perf/memory-limiter">MemoryLimiter</a> kills.
 *
 * <p>Checks Android's {@link ApplicationExitInfo} records on app start, finds exits that match the
 * MemoryLimiter signature, and turns them into Sentry events.
 *
 * <p><b>Data generated</b>
 *
 * <p>Each matching exit is reported as a synthetic fatal event with a {@code MemoryLimitExceeded}
 * exception. The original Android exit description is stored together with MemoryLimiter-specific
 * context in {@code mechanism.data}, including a {@link ApplicationExitInfo#getImportance()
 * process_importance} value and a derived {@link MemoryLimiterPolicy#toMemoryLimitClass
 * memory_limit_class}.
 *
 * <p>Events may also be backfilled with state persisted from the crashed app process, including
 * release info, environment, and other scope data.
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
  static final @NotNull String MEMORY_LIMITER_FINGERPRINT = "memory-limiter";
  static final @NotNull String MEMORY_LIMITER_MECHANISM = "memory_limiter";
  static final @NotNull String MEMORY_LIMITER_MESSAGE_PREFIX =
      "Android process killed by MemoryLimiter";

  static final @NotNull String MEMORY_LIMIT_CLASS_DATA_KEY = "memory_limit_class";
  static final @NotNull String MEMORY_LIMIT_CLASS_CACHED = "cached";
  static final @NotNull String MEMORY_LIMIT_CLASS_NOT_VISIBLE = "not_visible";
  static final @NotNull String MEMORY_LIMIT_CLASS_VISIBLE = "visible";

  static final @NotNull String PROCESS_IMPORTANCE_DATA_KEY = "process_importance";
  static final @NotNull String PROCESS_IMPORTANCE_FINGERPRINT_PREFIX = "process_importance:";

  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private @Nullable SentryAndroidOptions androidOptions;

  public MemoryLimiterIntegration(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    // Use CurrentDateProvider instead of AndroidCurrentDateProvider as ApplicationExitInfo uses
    // epochal System.currentTimeMillis and not time since boot.
    this(context, CurrentDateProvider.getInstance(), buildInfoProvider);
  }

  @TestOnly
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
     * Returns true if the provided {@code exitInfo} looks like it came from a MemoryLimiter-induced
     * process death.
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
      // MemoryLimiter:Memory and MemoryLimiter:Swap, but for now doesn't kill the process because
      // of them.)
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

      final int processImportance = exitInfo.getImportance();
      final String memoryLimit = toMemoryLimitClass(processImportance);

      final Message message = new Message();
      final String messageText = toMessage(processImportance);
      message.setFormatted(messageText);

      final SentryEvent event = new SentryEvent();
      event.setLevel(SentryLevel.FATAL);
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
      event.setTimestamp(DateUtils.getDateTime(timestamp));
      event.setMessage(message);
      event.setExceptions(
          Collections.singletonList(
              buildException(exitInfo, shouldEnrich, processImportance, memoryLimit, messageText)));
      event.setFingerprints(
          Arrays.asList(
              MEMORY_LIMITER_FINGERPRINT,
              PROCESS_IMPORTANCE_FINGERPRINT_PREFIX + processImportance));

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, memoryLimiterHint);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private @NotNull SentryException buildException(
        final @NotNull ApplicationExitInfo exitInfo,
        final boolean shouldEnrich,
        final int processImportance,
        final @NotNull String memoryLimit,
        final @NotNull String messageText) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(shouldEnrich ? "AppExitInfo" : "HistoricalAppExitInfo");
      mechanism.setDescription(exitInfo.getDescription());
      mechanism.setHandled(false);
      mechanism.setSynthetic(true);
      mechanism.setData(buildMechanismData(processImportance, memoryLimit));

      final SentryException sentryException = new SentryException();
      sentryException.setType("MemoryLimitExceeded");
      sentryException.setValue(messageText);
      sentryException.setModule("io.sentry.android.core");
      sentryException.setMechanism(mechanism);
      return sentryException;
    }

    private @NotNull Map<String, Object> buildMechanismData(
        final int processImportance, final @NotNull String memoryLimit) {
      final Map<String, Object> data = new HashMap<>();
      data.put(PROCESS_IMPORTANCE_DATA_KEY, toImportanceDescription(processImportance));
      data.put(MEMORY_LIMIT_CLASS_DATA_KEY, memoryLimit);
      return data;
    }

    /**
     * Best-effort mapping from {@link ApplicationExitInfo#getImportance()} to MemoryLimiter's <a
     * href="https://source.android.com/docs/core/perf/memory-limiter#process-monitoring">visible,
     * not-visible, or cached</a> classifications.
     *
     * <p>Mappings are inexact because MemoryLimiter determines category membership from {@code
     * PROCESS_STATE_*} values, but {@link ApplicationExitInfo} only exposes a coarser {@link
     * RunningAppProcessInfo} importance bucket.
     */
    private @NotNull String toMemoryLimitClass(final int processImportance) {
      switch (processImportance) {
        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
        // Docs say IMPORTANCE_TOP_SLEEPING isn't visible to users but MemoryLimiter treats is as a
        // "visible" class (cf. docs linked in this method's Javadoc).
        case RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
        // Over-classifies some exits as visible b/c IMPORTANCE_VISIBLE corresponds to
        // PROCESS_STATE_IMPORTANT_FOREGROUND (visible) and PROCESS_STATE_IMPORTANT_BACKGROUND (not
        // visible).
        case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
          return MEMORY_LIMIT_CLASS_VISIBLE;

        case RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
        case RunningAppProcessInfo.IMPORTANCE_GONE:
        case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
        case RunningAppProcessInfo.IMPORTANCE_SERVICE:
          return MEMORY_LIMIT_CLASS_NOT_VISIBLE;

        case RunningAppProcessInfo.IMPORTANCE_CACHED:
        default:
          // Fall back to the least specific bucket.
          return MEMORY_LIMIT_CLASS_CACHED;
      }
    }

    private @NotNull String toMessage(final int processImportance) {
      return MEMORY_LIMITER_MESSAGE_PREFIX
          + " (importance: "
          + toImportanceLabel(processImportance)
          + ")";
    }

    private @NotNull String toImportanceDescription(final int processImportance) {
      return processImportance + " (" + toImportanceLabel(processImportance) + ")";
    }

    private @NotNull String toImportanceLabel(final int processImportance) {
      switch (processImportance) {
        case RunningAppProcessInfo.IMPORTANCE_CACHED:
          return "cached";
        case RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
          return "cant_save_state";
        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
          return "foreground";
        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
          return "foreground_service";
        case RunningAppProcessInfo.IMPORTANCE_GONE:
          return "gone";
        case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
          return "perceptible";
        case RunningAppProcessInfo.IMPORTANCE_SERVICE:
          return "service";
        case RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
          return "top_sleeping";
        case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
          return "visible";
        default:
          return "unknown";
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
      implements Backfillable, AbnormalExit {

    private final long epochTimestampMs;
    private final boolean shouldEnrich;

    public MemoryLimiterHint(
        final long flushTimeoutMillis,
        final @NotNull ILogger logger,
        final long epochTimestampMs,
        final boolean shouldEnrich) {
      super(flushTimeoutMillis, logger);
      this.epochTimestampMs = epochTimestampMs;
      this.shouldEnrich = shouldEnrich;
    }

    @Override
    public @NotNull Long timestamp() {
      return epochTimestampMs;
    }

    @Override
    public @NotNull String mechanism() {
      return MEMORY_LIMITER_MECHANISM;
    }

    @Override
    public boolean ignoreCurrentThread() {
      return false;
    }

    @Override
    public boolean shouldEnrich() {
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
