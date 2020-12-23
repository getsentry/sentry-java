package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface ITransaction extends ISpan {

  /**
   * Sets transaction name.
   *
   * @param name - transaction name
   */
  void setName(@NotNull String name);

  /**
   * Starts a child Span.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  @NotNull
  Span startChild(final @NotNull SpanId parentSpanId);

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - span operation name
   * @param description - span description
   * @return a new transaction span
   */
  @NotNull
  Span startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @NotNull String description);

  /**
   * Attaches request information to the transaction.
   *
   * @param request the request
   */
  void setRequest(Request request);

  /**
   * Returns the request information from the transaction
   *
   * @return the request or {@code null} if not set
   */
  Request getRequest();

  Contexts getContexts();

  void setContexts(Contexts contexts);

  /**
   * Returns the transaction's description.
   *
   * @return the description
   */
  @Nullable
  String getDescription();

  @NotNull
  @TestOnly
  List<Span> getSpans();

  /**
   * Returns if transaction is sampled.
   *
   * @return is sampled
   */
  @Nullable
  Boolean isSampled();

  /**
   * Returns the latest span that is not finished.
   *
   * @return span or null if not found.
   */
  @Nullable
  Span getLatestActiveSpan();

  /**
   * Returns transaction's event id.
   *
   * @return the event id
   */
  @Nullable
  SentryId getEventId();

  @Nullable
  @ApiStatus.Internal
  String getTransaction();
}
