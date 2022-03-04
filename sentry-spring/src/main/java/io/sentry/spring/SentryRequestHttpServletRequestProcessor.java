package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;
import io.sentry.spring.tracing.TransactionNameProvider;
import io.sentry.util.Objects;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches transaction name from the HTTP request to {@link SentryEvent}. */
@Open
public class SentryRequestHttpServletRequestProcessor implements EventProcessor {
  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull HttpServletRequest request;

  public SentryRequestHttpServletRequestProcessor(
      final @NotNull TransactionNameProvider transactionNameProvider,
      final @NotNull HttpServletRequest request) {
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
    this.request = Objects.requireNonNull(request, "request is required");
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Map<String, Object> hint) {
    if (event.getTransaction() == null) {
      event.setTransaction(transactionNameProvider.provideTransactionName(request));
    }
    return event;
  }
}
