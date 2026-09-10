package io.sentry.android.core;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

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
import java.util.Collections;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO ADAM: Create sample app and test manually.
// TODO ADAM: Other due diligence: perf, memory leaks, host app crashes. Adequate guarding based on
//   Android API level.
// TODO ADAM: Other potential functionality:
//  - Report heap dumps (discussed here:
//  https://android-developers.googleblog.com/2026/06/prioritizing-memory-efficiency-steps-for-android-17.html)
//  - Use ProfilingManager anomaly/OOM triggers for pre-kill heap-dump diagnostics.
//  - Differentiate btw MemoryLimiter kills for visible, not-visible, and cached processes rather than
//    reporting a generic synthetic fatal event. (Cf. ApplicationExitInfo.getImportance(), etc.).
//  - React to onTrimMemory for proactive shedding.
//  - Our existing trim-memory breadcrumb logic ignores TRIM_MEMORY_UI_HIDDEN entirely, as it only records
//    levels >= TRIM_MEMORY_BACKGROUND. MemoryLimiter blog post linked above mentions focusing on both.

/**
 * Reports Android process deaths that the OS records as <a
 * href="https://source.android.com/docs/core/perf/memory-limiter">MemoryLimiter</a> kills.
 *
 * <p>Checks Android's historical {@link ApplicationExitInfo} records on the app start, finds exits
 * that match the MemoryLimiter signature, and turns them into synthetic Sentry events. Those events
 * can then be backfilled with persisted SDK state, such as release and environment, in the same
 * recovered-exit pipeline used for other {@link ApplicationExitInfo} based reports.
 *
 * <p>Only available on Android API 37+.
 */
@ApiStatus.Internal
public final class MemoryLimiterIntegration implements Integration, Closeable {

  // TODO ADAM: Where does this description come from? INclude a reference.
  static final @NotNull String MEMORY_LIMITER_DESCRIPTION = "MemoryLimiter:AnonSwap";
  static final @NotNull String MEMORY_LIMITER_MESSAGE = "Android process killed by MemoryLimiter";

  private final @NotNull Context context;
  // TODO ADAM: Use one of the new internal time APIs, eg, EpochClock or AnchoredClock instead of
  // ICurrentDateProvider?
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable SentryAndroidOptions androidOptions;

  public MemoryLimiterIntegration(final @NotNull Context context) {
    // We use CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis.
    this(context, CurrentDateProvider.getInstance());
  }

  MemoryLimiterIntegration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    // TODO ADAM: Throw or log error? What do other integrations do?
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

    if (this.androidOptions.getCacheDirPath() == null) {
      this.androidOptions
          .getLogger()
          // TODO ADAM: WARNING level instead of INFO? (Audit all log levels, comparing against what
          // we do elsewhere.)
          .log(INFO, "Cache dir is not set, unable to process MemoryLimiter kills");
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
    // TODO ADAM: Do we normally call addIntegrationToSdkVersion in Integration.register()?
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
      return "MemoryLimiter kill";
    }

    /**
     * Matches Android exits that the system records as generic "other" deaths and annotates with
     * the MemoryLimiter description.
     */
    @Override
    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean matches(final @NotNull ApplicationExitInfo exitInfo) {
      // TODO ADAM: Explain why we know REASON_OTHER + MEMORY_LIMITER_DESCRIPTION captures all
      //  memory kill situations and nothing else.
      if (exitInfo.getReason() != ApplicationExitInfo.REASON_OTHER) {
        return false;
      }
      final String description = exitInfo.getDescription();
      return description != null && description.contains(MEMORY_LIMITER_DESCRIPTION);
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalMemoryLimiterKills();
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
      final MemoryLimiterHint hint =
          new MemoryLimiterHint(
              options.getFlushTimeoutMillis(), options.getLogger(), timestamp, shouldEnrich);
      final Hint sentryHint = HintUtils.createWithTypeCheckHint(hint);

      final SentryEvent event = new SentryEvent();
      final Message message = new Message();
      message.setFormatted(MEMORY_LIMITER_MESSAGE);
      event.setMessage(message);
      event.setLevel(SentryLevel.FATAL);
      // TODO ADAM: Why are we using the DEFAULT_PLATFORM (== java) rather than Android?
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
      // TODO ADAM: Use Nelson's new clocks?
      event.setTimestamp(DateUtils.getDateTime(timestamp));
      event.setExceptions(Collections.singletonList(buildException(exitInfo, shouldEnrich)));

      return new ApplicationExitInfoHistoryDispatcher.Report(event, sentryHint, hint);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private @NotNull SentryException buildException(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(shouldEnrich ? "AppExitInfo" : "HistoricalAppExitInfo");
      mechanism.setDescription(exitInfo.getDescription());
      mechanism.setHandled(false);
      mechanism.setSynthetic(true);

      final SentryException sentryException = new SentryException();
      sentryException.setType("MemoryLimitExceeded");
      sentryException.setValue(MEMORY_LIMITER_MESSAGE);
      sentryException.setModule("io.sentry.android.core");
      sentryException.setMechanism(mechanism);
      return sentryException;
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
  public static final class MemoryLimiterHint extends BlockingFlushHint implements Backfillable {

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

    public long timestamp() {
      return timestamp;
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
