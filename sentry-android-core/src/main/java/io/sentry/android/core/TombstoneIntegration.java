package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.sentry.Attachment;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy;
import io.sentry.android.core.NativeEventCollector.NativeEventData;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.tombstone.NativeExceptionMechanism;
import io.sentry.android.core.internal.tombstone.TombstoneParser;
import io.sentry.hints.Backfillable;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.hints.NativeCrashExit;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryThread;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class TombstoneIntegration implements Integration, Closeable {
  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable SentryAndroidOptions options;

  public TombstoneIntegration(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  TombstoneIntegration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "TombstoneIntegration enabled: %s",
            this.options.isTombstoneEnabled());

    if (this.options.isTombstoneEnabled()) {
      if (this.options.getCacheDirPath() == null) {
        this.options
            .getLogger()
            .log(SentryLevel.INFO, "Cache dir is not set, unable to process Tombstones");
        return;
      }

      try {
        options
            .getExecutorService()
            .submit(
                new ApplicationExitInfoHistoryDispatcher(
                    context,
                    scopes,
                    this.options,
                    dateProvider,
                    new TombstonePolicy(this.options, this.context)));
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.DEBUG, "Failed to start tombstone processor.", e);
      }
      options.getLogger().log(SentryLevel.DEBUG, "TombstoneIntegration installed.");
      addIntegrationToSdkVersion("Tombstone");
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "TombstoneIntegration removed.");
    }
  }

  @ApiStatus.Internal
  public static class TombstonePolicy implements ApplicationExitInfoPolicy {

    private final @NotNull SentryAndroidOptions options;
    private final @NotNull NativeEventCollector nativeEventCollector;
    @NotNull private final Context context;

    public TombstonePolicy(final @NotNull SentryAndroidOptions options, @NotNull Context context) {
      this.options = options;
      this.nativeEventCollector = new NativeEventCollector(options);
      this.context = context;
    }

    @Override
    public @NotNull String getLabel() {
      return "Tombstone";
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public int getTargetReason() {
      return ApplicationExitInfo.REASON_CRASH_NATIVE;
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalTombstones();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedTombstone(options);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public @Nullable ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean enrich) {
      SentryEvent event;
      try {
        final InputStream tombstoneInputStream = exitInfo.getTraceInputStream();
        if (tombstoneInputStream == null) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "No tombstone InputStream available for ApplicationExitInfo from %s",
                  DateTimeFormatter.ISO_INSTANT.format(
                      Instant.ofEpochMilli(exitInfo.getTimestamp())));
          return null;
        }

        try (final TombstoneParser parser =
            new TombstoneParser(
                tombstoneInputStream,
                this.options.getInAppIncludes(),
                this.options.getInAppExcludes(),
                this.context.getApplicationInfo().nativeLibraryDir)) {
          event = parser.parse();
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to parse tombstone from %s: %s",
                DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(exitInfo.getTimestamp())),
                e.getMessage());
        return null;
      }

      final long tombstoneTimestamp = exitInfo.getTimestamp();
      event.setTimestamp(DateUtils.getDateTime(tombstoneTimestamp));

      final TombstoneHint tombstoneHint =
          new TombstoneHint(
              options.getFlushTimeoutMillis(), options.getLogger(), tombstoneTimestamp, enrich);
      final Hint hint = HintUtils.createWithTypeCheckHint(tombstoneHint);

      try {
        final @Nullable SentryEvent mergedEvent =
            mergeWithMatchingNativeEvents(tombstoneTimestamp, event, hint);
        if (mergedEvent != null) {
          event = mergedEvent;
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to merge native event with tombstone, continuing without merge: %s",
                e.getMessage());
      }

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, tombstoneHint);
    }

    /**
     * Attempts to find a matching native SDK event for the tombstone and merge them.
     *
     * @return The merged native event (with tombstone data applied) if a match was found and
     *     merged, or null if no matching event was found or merge failed.
     */
    private @Nullable SentryEvent mergeWithMatchingNativeEvents(
        long tombstoneTimestamp, SentryEvent tombstoneEvent, Hint hint) {
      // Try to find and remove matching native event from outbox
      final @Nullable NativeEventData matchingNativeEvent =
          nativeEventCollector.findAndRemoveMatchingNativeEvent(tombstoneTimestamp);

      if (matchingNativeEvent == null) {
        options.getLogger().log(SentryLevel.DEBUG, "No matching native event found for tombstone.");
        return null;
      }

      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Found matching native event for tombstone, removing from outbox: %s",
              matchingNativeEvent.getFile().getName());

      // Delete from outbox so OutboxSender doesn't send it
      boolean deletionSuccess = nativeEventCollector.deleteNativeEventFile(matchingNativeEvent);

      if (deletionSuccess) {
        final SentryEvent nativeEvent = matchingNativeEvent.getEvent();
        mergeNativeCrashes(nativeEvent, tombstoneEvent);
        addNativeAttachmentsToTombstoneHint(matchingNativeEvent, hint);
        return nativeEvent;
      }
      return null;
    }

    private void addNativeAttachmentsToTombstoneHint(
        @NonNull NativeEventData matchingNativeEvent, Hint hint) {
      @NotNull SentryEnvelope nativeEnvelope = matchingNativeEvent.getEnvelope();
      for (SentryEnvelopeItem item : nativeEnvelope.getItems()) {
        try {
          @Nullable String attachmentFileName = item.getHeader().getFileName();
          if (item.getHeader().getType() != SentryItemType.Attachment
              || attachmentFileName == null) {
            continue;
          }
          hint.addAttachment(
              new Attachment(
                  item.getData(),
                  attachmentFileName,
                  item.getHeader().getContentType(),
                  item.getHeader().getAttachmentType(),
                  false));
        } catch (Throwable e) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Failed to process envelope item: %s", e.getMessage());
        }
      }
    }

    private void mergeNativeCrashes(
        final @NotNull SentryEvent nativeEvent, final @NotNull SentryEvent tombstoneEvent) {
      // we take the event data verbatim from the Native SDK and only apply tombstone data where we
      // are sure that it will improve the outcome:
      // * context from the Native SDK will be closer to what users want than any backfilling
      // * the Native SDK only tracks the crashing thread  (vs. tombstone dumps all)
      // * even for the crashing  we expect a much better stack-trace (+ symbolication)
      // * tombstone adds additional exception meta-data to signal handler content
      // * we add debug-meta for consistency since the Native SDK caches memory maps early
      @Nullable List<SentryException> tombstoneExceptions = tombstoneEvent.getExceptions();
      @Nullable DebugMeta tombstoneDebugMeta = tombstoneEvent.getDebugMeta();
      @Nullable List<SentryThread> tombstoneThreads = tombstoneEvent.getThreads();
      if (tombstoneExceptions != null
          && !tombstoneExceptions.isEmpty()
          && tombstoneDebugMeta != null
          && tombstoneThreads != null) {
        // native crashes don't nest, we always expect one level.
        SentryException exception = tombstoneExceptions.get(0);
        @Nullable Mechanism mechanism = exception.getMechanism();
        if (mechanism != null) {
          mechanism.setType(NativeExceptionMechanism.TOMBSTONE_MERGED.getValue());
        }
        nativeEvent.setExceptions(tombstoneExceptions);
        nativeEvent.setDebugMeta(tombstoneDebugMeta);
        nativeEvent.setThreads(tombstoneThreads);
      }
    }
  }

  @ApiStatus.Internal
  public static final class TombstoneHint extends BlockingFlushHint
      implements Backfillable, NativeCrashExit {

    private final long tombstoneTimestamp;
    private final boolean shouldEnrich;

    public TombstoneHint(
        long flushTimeoutMillis,
        @NotNull ILogger logger,
        long tombstoneTimestamp,
        boolean shouldEnrich) {
      super(flushTimeoutMillis, logger);
      this.tombstoneTimestamp = tombstoneTimestamp;
      this.shouldEnrich = shouldEnrich;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return tombstoneTimestamp;
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
