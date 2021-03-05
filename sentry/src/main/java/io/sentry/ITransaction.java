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
   * Returns transaction name.
   *
   * @return transaction name
   */
  @NotNull
  String getName();

  /**
   * Attaches request information to the transaction.
   *
   * @param request the request
   * @deprecated use {@link Scope#setRequest(Request)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void setRequest(@Nullable Request request);

  /**
   * Returns the request information from the transaction
   *
   * @return the request or {@code null} if not set
   * @deprecated use {@link Scope#getRequest()}
   */
  @Nullable
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Request getRequest();

  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Contexts getContexts();

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

  /**
   * Returns the transaction name.
   *
   * @deprecated use {@link #getName()}
   * @return transaction name
   */
  @Nullable
  @ApiStatus.Internal
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  String getTransaction();
}
