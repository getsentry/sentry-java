package io.sentry.android.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

import io.sentry.EventProcessor;
import io.sentry.protocol.SentryTransaction;

// TODO: does EventProcessor make sense? going with it just as a POC
final class AndroidPerformanceEventProcessor implements EventProcessor {

    private @NotNull final Date appStartTime;

    private static final String START_TIME_KEY = "sentry:app_start_time_end";

    AndroidPerformanceEventProcessor(@NotNull final Date appStartTime) {
        this.appStartTime = appStartTime;
    }

    @SuppressWarnings("JavaUtilDate")
    @Override
    public @NotNull SentryTransaction process(
            final @NotNull SentryTransaction transaction, final @Nullable Object hint) {
        // getMeasurements() isnt part of the public interface in ITransaction and ISpan
        // so right now using it in event processors and adding to every transaction
        // obviously this is not ideal.
        // Contexts are part of ITransaction though
        final Object appStartTimeEndObj = transaction.getContexts().get(START_TIME_KEY);
        if (appStartTimeEndObj != null) {
            final Date appStartTimeEnd = (Date)appStartTimeEndObj;
            final long totalMillis = appStartTimeEnd.getTime() - appStartTime.getTime();

            // values get dropped because its an unknown field in relay
            transaction.getMeasurements().put("app_start_time", totalMillis);
            transaction.getContexts().remove(START_TIME_KEY);
        }

        return transaction;
    }
}
