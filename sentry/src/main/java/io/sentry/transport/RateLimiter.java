package io.sentry.transport;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;

import io.sentry.DataCategory;
import io.sentry.Hint;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.clientreport.DiscardReason;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.time.Deadline;
import io.sentry.time.MonotonicClock;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HintUtils;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Controls retry limits on different category types sent to Sentry. */
public final class RateLimiter implements Closeable {

  private static final long HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS = 60_000;

  private final @NotNull MonotonicClock clock;
  private final @NotNull RateLimiterConfig config;
  private final @NotNull Map<DataCategory, @NotNull Deadline> sentryRetryAfterLimit =
      new ConcurrentHashMap<>();
  private final @NotNull List<IRateLimitObserver> rateLimitObservers = new CopyOnWriteArrayList<>();
  private final @NotNull List<Future<?>> notifyObserversFutures = new ArrayList<>();
  private final @NotNull AutoClosableReentrantLock notifyFuturesLock =
      new AutoClosableReentrantLock();

  public RateLimiter(final @NotNull MonotonicClock clock, final @NotNull RateLimiterConfig config) {
    this.clock = clock;
    this.config = config;
  }

  public RateLimiter(final @NotNull SentryOptions options) {
    this(options.getMonotonicClock(), options);
  }

  /**
   * @deprecated backoff is measured on {@link SentryOptions#getMonotonicClock()}; use {@link
   *     #RateLimiter(SentryOptions)}. An injected wall clock is adapted so that an existing custom
   *     transport keeps the behaviour it has today, but it is not monotonic and can step.
   */
  @Deprecated
  public RateLimiter(
      final @NotNull ICurrentDateProvider currentDateProvider,
      final @NotNull SentryOptions options) {
    // ICurrentDateProvider is itself a `long ()` interface, so an unadorned lambda matches this
    // constructor as readily as the intended one. The cast is what makes the call non-recursive.
    this(
        (MonotonicClock)
            () -> TimeUnit.MILLISECONDS.toNanos(currentDateProvider.getCurrentTimeMillis()),
        options);
  }

  public @Nullable SentryEnvelope filter(
      final @NotNull SentryEnvelope envelope, final @NotNull Hint hint) {
    // Optimize for/No allocations if no items are under 429
    List<SentryEnvelopeItem> dropItems = null;
    for (SentryEnvelopeItem item : envelope.getItems()) {
      // using the raw value of the enum to not expose SentryEnvelopeItemType
      if (isRetryAfter(item.getHeader().getType().getItemType())) {
        if (dropItems == null) {
          dropItems = new ArrayList<>();
        }

        dropItems.add(item);
        config
            .getClientReportRecorder()
            .recordLostEnvelopeItem(DiscardReason.RATELIMIT_BACKOFF, item);
      }
    }

    if (dropItems != null) {
      config
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "%d envelope items will be dropped due rate limiting.",
              dropItems.size());

      //       Need a new envelope
      List<SentryEnvelopeItem> toSend = new ArrayList<>();
      for (SentryEnvelopeItem item : envelope.getItems()) {
        if (!dropItems.contains(item)) {
          toSend.add(item);
        }
      }

      // no reason to continue
      if (toSend.isEmpty()) {
        config
            .getLogger()
            .log(SentryLevel.WARNING, "Envelope discarded due all items rate limited.");

        markHintWhenSendingFailed(hint, false);
        return null;
      }

      return new SentryEnvelope(envelope.getHeader(), toSend);
    }
    return envelope;
  }

  public boolean isActiveForCategory(final @NotNull DataCategory dataCategory) {
    // check all categories
    final @Nullable Deadline allCategories = sentryRetryAfterLimit.get(DataCategory.All);
    if (allCategories != null && !allCategories.hasPassed()) {
      return true;
    }

    // Unknown should not be rate limited
    if (DataCategory.Unknown.equals(dataCategory)) {
      return false;
    }

    // check for specific dataCategory
    final @Nullable Deadline categoryLimit = sentryRetryAfterLimit.get(dataCategory);
    return categoryLimit != null && !categoryLimit.hasPassed();
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public boolean isAnyRateLimitActive() {
    for (final @NotNull Deadline limit : sentryRetryAfterLimit.values()) {
      if (!limit.hasPassed()) {
        return true;
      }
    }

    return false;
  }

  /**
   * It marks the hint when sending has failed, so it's not necessary to wait the timeout
   *
   * @param hint the Hints
   * @param retry if event should be retried or not
   */
  private void markHintWhenSendingFailed(final @NotNull Hint hint, final boolean retry) {
    HintUtils.runIfHasType(hint, SubmissionResult.class, result -> result.setResult(false));
    HintUtils.runIfHasType(hint, Retryable.class, retryable -> retryable.setRetry(retry));
    HintUtils.runIfHasType(
        hint,
        DiskFlushNotification.class,
        (diskFlushNotification) -> {
          diskFlushNotification.markFlushed();
          config.getLogger().log(SentryLevel.DEBUG, "Disk flush envelope fired due to rate limit");
        });
  }

  /**
   * Check if an itemType is retry after or not
   *
   * @param itemType the itemType (eg event, session, etc...)
   * @return true if retry after or false otherwise
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  private boolean isRetryAfter(final @NotNull String itemType) {
    final List<DataCategory> dataCategory = getCategoryFromItemType(itemType);
    for (DataCategory category : dataCategory) {
      if (isActiveForCategory(category)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a rate limiting category from item itemType
   *
   * @param itemType the item itemType (eg event, session, attachment, ...)
   * @return the DataCategory eg (DataCategory.Error, DataCategory.Session, DataCategory.Attachment)
   */
  private @NotNull List<DataCategory> getCategoryFromItemType(final @NotNull String itemType) {
    switch (itemType) {
      case "event":
        return Collections.singletonList(DataCategory.Error);
      case "session":
        return Collections.singletonList(DataCategory.Session);
      case "attachment":
        return Collections.singletonList(DataCategory.Attachment);
      case "profile":
        return Collections.singletonList(DataCategory.Profile);
      // When we send a profile chunk, we have to check for profile_chunk_ui rate limiting,
      // because that's what relay returns to rate limit Android.
      // And ProfileChunk rate limiting for JVM.
      case "profile_chunk":
        return Arrays.asList(DataCategory.ProfileChunkUi, DataCategory.ProfileChunk);
      case "transaction":
        return Collections.singletonList(DataCategory.Transaction);
      case "check_in":
        return Collections.singletonList(DataCategory.Monitor);
      case "replay_video":
        return Collections.singletonList(DataCategory.Replay);
      case "feedback":
        return Collections.singletonList(DataCategory.Feedback);
      case "log":
        return Arrays.asList(DataCategory.LogItem, DataCategory.LogByte);
      case "span":
        return Collections.singletonList(DataCategory.Span);
      case "trace_metric":
        return Arrays.asList(DataCategory.TraceMetric, DataCategory.TraceMetricByte);
      default:
        return Collections.singletonList(DataCategory.Unknown);
    }
  }

  /**
   * Reads and update the rate limit Dictionary
   *
   * @param sentryRateLimitHeader the sentry rate limit header
   * @param retryAfterHeader the retry after header
   * @param errorCode the error code if set
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public void updateRetryAfterLimits(
      final @Nullable String sentryRateLimitHeader,
      final @Nullable String retryAfterHeader,
      final int errorCode) {
    // example: 2700:metric_bucket:organization:quota_exceeded:custom,...
    if (sentryRateLimitHeader != null) {
      for (String limit : sentryRateLimitHeader.split(",", -1)) {

        // Java 11 or so has strip() :(
        limit = limit.replace(" ", "");

        final String[] rateLimit = limit.split(":", -1);
        // These can be ignored by the SDK.
        // final String scope = rateLimit.length > 2 ? rateLimit[2] : null;
        // final String reasonCode = rateLimit.length > 3 ? rateLimit[3] : null;
        // final @Nullable String limitNamespaces = rateLimit.length > 4 ? rateLimit[4] : null;

        if (rateLimit.length > 0) {
          final String retryAfter = rateLimit[0];
          final @NotNull Deadline deadline = parseRetryAfterOrDefault(retryAfter);

          if (rateLimit.length > 1) {
            final String allCategories = rateLimit[1];

            if (allCategories != null && !allCategories.isEmpty()) {
              final String[] categories = allCategories.split(";", -1);

              for (final String catItem : categories) {
                DataCategory dataCategory = DataCategory.Unknown;
                try {
                  final String catItemCapitalized = StringUtils.camelCase(catItem);
                  if (catItemCapitalized != null) {
                    dataCategory = DataCategory.valueOf(catItemCapitalized);
                  } else {
                    config.getLogger().log(ERROR, "Couldn't capitalize: %s", catItem);
                  }
                } catch (IllegalArgumentException e) {
                  config.getLogger().log(INFO, e, "Unknown category: %s", catItem);
                }
                // we dont apply rate limiting for unknown categories
                if (DataCategory.Unknown.equals(dataCategory)) {
                  continue;
                }

                applyRetryAfterOnlyIfLonger(dataCategory, deadline);
              }
            } else {
              // if categories are empty, we should apply to "all" categories.
              applyRetryAfterOnlyIfLonger(DataCategory.All, deadline);
            }
          }
        }
      }
    } else if (errorCode == 429) {
      applyRetryAfterOnlyIfLonger(DataCategory.All, parseRetryAfterOrDefault(retryAfterHeader));
    }
  }

  /**
   * apply the new deadline for rate limiting only if it is longer than the previous one
   *
   * @param dataCategory the DataCategory
   * @param deadline when the rate limit is lifted
   */
  private void applyRetryAfterOnlyIfLonger(
      final @NotNull DataCategory dataCategory, final @NotNull Deadline deadline) {
    final @Nullable Deadline oldLimit = sentryRetryAfterLimit.get(dataCategory);

    // only overwrite the previous deadline if the limit is even longer
    if (oldLimit == null || deadline.isAfter(oldLimit)) {
      sentryRetryAfterLimit.put(dataCategory, deadline);

      notifyRateLimitObservers();

      // notify observers again once the rate limit is lifted, using the shared timer executor
      // instead of a dedicated Timer thread
      try (final @NotNull ISentryLifecycleToken ignored = notifyFuturesLock.acquire()) {
        final @NotNull Iterator<Future<?>> iterator = notifyObserversFutures.iterator();
        while (iterator.hasNext()) {
          if (iterator.next().isDone()) {
            iterator.remove();
          }
        }
        try {
          notifyObserversFutures.add(
              config
                  .getTimerExecutorService()
                  .schedule(
                      this::notifyRateLimitObservers, deadline.remaining(TimeUnit.MILLISECONDS)));
        } catch (RejectedExecutionException e) {
          config
              .getLogger()
              .log(SentryLevel.WARNING, "Failed to schedule rate limit lifted notification.", e);
        }
      }
    }
  }

  /**
   * Parses a millis string to a seconds number
   *
   * @param retryAfterHeader the header
   * @return the millis in seconds or the default seconds value
   */
  private @NotNull Deadline parseRetryAfterOrDefault(final @Nullable String retryAfterHeader) {
    long retryAfterMillis = HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS;
    if (retryAfterHeader != null) {
      try {
        retryAfterMillis =
            (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
      } catch (NumberFormatException ignored) {
        // let's use the default then
      }
    }
    return Deadline.after(clock, retryAfterMillis, TimeUnit.MILLISECONDS);
  }

  private void notifyRateLimitObservers() {
    for (IRateLimitObserver observer : rateLimitObservers) {
      observer.onRateLimitChanged(this);
    }
  }

  public void addRateLimitObserver(@NotNull final IRateLimitObserver observer) {
    rateLimitObservers.add(observer);
  }

  public void removeRateLimitObserver(@NotNull final IRateLimitObserver observer) {
    rateLimitObservers.remove(observer);
  }

  @Override
  public void close() throws IOException {
    try (final @NotNull ISentryLifecycleToken ignored = notifyFuturesLock.acquire()) {
      for (Future<?> future : notifyObserversFutures) {
        future.cancel(false);
      }
      notifyObserversFutures.clear();
    }
    rateLimitObservers.clear();
  }

  public interface IRateLimitObserver {
    /**
     * Invoked whenever the rate limit changed. You should use {@link
     * RateLimiter#isActiveForCategory(DataCategory)} to check whether the category you're
     * interested in has changed.
     *
     * @param rateLimiter this {@link RateLimiter} instance which you can use to check if the rate
     *     limit is active for a specific category
     */
    void onRateLimitChanged(@NotNull RateLimiter rateLimiter);
  }
}
