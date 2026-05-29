package io.sentry.android.core;

import io.sentry.TransactionContext;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * How the first {@code ui.load} transaction of a process start should be created when standalone
 * app-start tracing is enabled.
 */
@ApiStatus.Internal
final class UiLoadStartPlan {

  private final @NotNull TransactionContext transactionContext;
  private final boolean emitSiblingAppStart;

  UiLoadStartPlan(
      final @NotNull TransactionContext transactionContext, final boolean emitSiblingAppStart) {
    this.transactionContext =
        Objects.requireNonNull(transactionContext, "transactionContext is required");
    this.emitSiblingAppStart = emitSiblingAppStart;
  }

  @NotNull
  TransactionContext getTransactionContext() {
    return transactionContext;
  }

  boolean shouldEmitSiblingAppStart() {
    return emitSiblingAppStart;
  }
}
