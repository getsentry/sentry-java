package io.sentry.transport;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;

import io.sentry.DataCategory;
import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.clientreport.DiscardReason;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.util.CollectionUtils;
import io.sentry.util.HintUtils;
import io.sentry.util.StringUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Controls retry limits on different category types sent to Sentry. */
public final class RateLimiter implements Closeable {

  private static final int HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS = 60000;

  private final @NotNull ICurrentDateProvider currentDateProvider;
  private final @NotNull SentryOptions options;
  private final @NotNull Map<DataCategory, @NotNull Date> sentryRetryAfterLimit =
      new ConcurrentHashMap<>();
  private final @NotNull List<IRateLimitObserver> rateLimitObservers = new CopyOnWriteArrayList<>();
  private @Nullable Timer timer = null;
  private final @NotNull Object timerLock = new Object();

  public RateLimiter(
      final @NotNull ICurrentDateProvider currentDateProvider,
      final @NotNull SentryOptions options) {
    this.currentDateProvider = currentDateProvider;
    this.options = options;
  }

  public RateLimiter(final @NotNull SentryOptions options) {
    this(CurrentDateProvider.getInstance(), options);
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
        options
            .getClientReportRecorder()
            .recordLostEnvelopeItem(DiscardReason.RATELIMIT_BACKOFF, item);
      }
    }

    if (dropItems != null) {
      options
          .getLogger()
          .log(SentryLevel.INFO, "%d items will be dropped due rate limiting.", dropItems.size());

      //       Need a new envelope
      List<SentryEnvelopeItem> toSend = new ArrayList<>();
      for (SentryEnvelopeItem item : envelope.getItems()) {
        if (!dropItems.contains(item)) {
          toSend.add(item);
        }
      }

      // no reason to continue
      if (toSend.isEmpty()) {
        options.getLogger().log(SentryLevel.INFO, "Envelope discarded due all items rate limited.");

        markHintWhenSendingFailed(hint, false);
        return null;
      }

      return new SentryEnvelope(envelope.getHeader(), toSend);
    }
    return envelope;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public boolean isActiveForCategory(final @NotNull DataCategory dataCategory) {
    final Date currentDate = new Date(currentDateProvider.getCurrentTimeMillis());

    // check all categories
    final Date dateAllCategories = sentryRetryAfterLimit.get(DataCategory.All);
    if (dateAllCategories != null) {
      if (!currentDate.after(dateAllCategories)) {
        return true;
      }
    }

    // Unknown should not be rate limited
    if (DataCategory.Unknown.equals(dataCategory)) {
      return false;
    }

    // check for specific dataCategory
    final Date dateCategory = sentryRetryAfterLimit.get(dataCategory);
    if (dateCategory != null) {
      return !currentDate.after(dateCategory);
    }

    return false;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public boolean isAnyRateLimitActive() {
    final Date currentDate = new Date(currentDateProvider.getCurrentTimeMillis());

    for (DataCategory dataCategory : sentryRetryAfterLimit.keySet()) {
      final Date dateCategory = sentryRetryAfterLimit.get(dataCategory);
      if (dateCategory != null) {
        if (!currentDate.after(dateCategory)) {
          return true;
        }
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
  private static void markHintWhenSendingFailed(final @NotNull Hint hint, final boolean retry) {
    HintUtils.runIfHasType(hint, SubmissionResult.class, result -> result.setResult(false));
    HintUtils.runIfHasType(hint, Retryable.class, retryable -> retryable.setRetry(retry));
  }

  /**
   * Check if an itemType is retry after or not
   *
   * @param itemType the itemType (eg event, session, etc...)
   * @return true if retry after or false otherwise
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  private boolean isRetryAfter(final @NotNull String itemType) {
    final DataCategory dataCategory = getCategoryFromItemType(itemType);
    return isActiveForCategory(dataCategory);
  }

  /**
   * Returns a rate limiting category from item itemType
   *
   * @param itemType the item itemType (eg event, session, attachment, ...)
   * @return the DataCategory eg (DataCategory.Error, DataCategory.Session, DataCategory.Attachment)
   */
  private @NotNull DataCategory getCategoryFromItemType(final @NotNull String itemType) {
    switch (itemType) {
      case "event":
        return DataCategory.Error;
      case "session":
        return DataCategory.Session;
      case "attachment":
        return DataCategory.Attachment;
      case "profile":
        return DataCategory.Profile;
        // The envelope item type used for metrics is statsd, whereas the client report category is
        // metric_bucket
      case "statsd":
        return DataCategory.MetricBucket;
      case "transaction":
        return DataCategory.Transaction;
      case "check_in":
        return DataCategory.Monitor;
      case "replay_video":
        return DataCategory.Replay;
      default:
        return DataCategory.Unknown;
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
        final @Nullable String limitNamespaces = rateLimit.length > 4 ? rateLimit[4] : null;

        if (rateLimit.length > 0) {
          final String retryAfter = rateLimit[0];
          long retryAfterMillis = parseRetryAfterOrDefault(retryAfter);

          if (rateLimit.length > 1) {
            final String allCategories = rateLimit[1];

            // we dont care if Date is UTC as we just add the relative seconds
            final Date date =
                new Date(currentDateProvider.getCurrentTimeMillis() + retryAfterMillis);

            if (allCategories != null && !allCategories.isEmpty()) {
              final String[] categories = allCategories.split(";", -1);

              for (final String catItem : categories) {
                DataCategory dataCategory = DataCategory.Unknown;
                try {
                  final String catItemCapitalized = StringUtils.camelCase(catItem);
                  if (catItemCapitalized != null) {
                    dataCategory = DataCategory.valueOf(catItemCapitalized);
                  } else {
                    options.getLogger().log(ERROR, "Couldn't capitalize: %s", catItem);
                  }
                } catch (IllegalArgumentException e) {
                  options.getLogger().log(INFO, e, "Unknown category: %s", catItem);
                }
                // we dont apply rate limiting for unknown categories
                if (DataCategory.Unknown.equals(dataCategory)) {
                  continue;
                }
                // SDK doesn't support namespaces, yet. Namespaces can be returned by relay in case
                // of metric_bucket items. If the namespaces are empty or contain "custom" we apply
                // the rate limit to all metrics, otherwise to none.
                if (DataCategory.MetricBucket.equals(dataCategory)
                    && limitNamespaces != null
                    && !limitNamespaces.equals("")) {
                  final String[] namespaces = limitNamespaces.split(";", -1);
                  if (namespaces.length > 0 && !CollectionUtils.contains(namespaces, "custom")) {
                    continue;
                  }
                }

                applyRetryAfterOnlyIfLonger(dataCategory, date);
              }
            } else {
              // if categories are empty, we should apply to "all" categories.
              applyRetryAfterOnlyIfLonger(DataCategory.All, date);
            }
          }
        }
      }
    } else if (errorCode == 429) {
      final long retryAfterMillis = parseRetryAfterOrDefault(retryAfterHeader);
      // we dont care if Date is UTC as we just add the relative seconds
      final Date date = new Date(currentDateProvider.getCurrentTimeMillis() + retryAfterMillis);
      applyRetryAfterOnlyIfLonger(DataCategory.All, date);
    }
  }

  /**
   * apply new timestamp for rate limiting only if its longer than the previous one
   *
   * @param dataCategory the DataCategory
   * @param date the Date to be applied
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  private void applyRetryAfterOnlyIfLonger(
      final @NotNull DataCategory dataCategory, final @NotNull Date date) {
    final Date oldDate = sentryRetryAfterLimit.get(dataCategory);

    // only overwrite its previous date if the limit is even longer
    if (oldDate == null || date.after(oldDate)) {
      sentryRetryAfterLimit.put(dataCategory, date);

      notifyRateLimitObservers(true);

      synchronized (timerLock) {
        if (timer == null) {
          timer = new Timer(true);
        }

        timer.schedule(
            new TimerTask() {
              @Override
              public void run() {
                notifyRateLimitObservers(false);
              }
            },
            date);
      }
    }
  }

  /**
   * Parses a millis string to a seconds number
   *
   * @param retryAfterHeader the header
   * @return the millis in seconds or the default seconds value
   */
  private long parseRetryAfterOrDefault(final @Nullable String retryAfterHeader) {
    long retryAfterMillis = HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS;
    if (retryAfterHeader != null) {
      try {
        retryAfterMillis =
            (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
      } catch (NumberFormatException ignored) {
        // let's use the default then
      }
    }
    return retryAfterMillis;
  }

  private void notifyRateLimitObservers(final boolean applied) {
    for (IRateLimitObserver observer : rateLimitObservers) {
      observer.onRateLimitChanged(applied);
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
    synchronized (timerLock) {
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
    }
  }

  public interface IRateLimitObserver {
    /**
     * Invoked whenever the rate limit changed. You should use {@link
     * RateLimiter#isActiveForCategory(DataCategory)} to check whether the category you're
     * interested in has changed.
     *
     * @param applied true if the rate limit was applied, false if it was lifted
     */
    void onRateLimitChanged(boolean applied);
  }
}
