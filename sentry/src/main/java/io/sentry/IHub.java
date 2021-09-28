package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SDK API contract which combines a client and scope management */
public interface IHub {

  /**
   * Check if the Hub is enabled/active.
   *
   * @return true if its enabled or false otherwise.
   */
  boolean isEnabled();

  /**
   * Captures the event.
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint);

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return captureEvent(event, null);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureMessage(@NotNull String message) {
    return captureMessage(message, SentryLevel.INFO);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Object hint);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope) {
    return captureEnvelope(envelope, null);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureException(@NotNull Throwable throwable, @Nullable Object hint);

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return captureException(throwable, null);
  }

  /**
   * Captures a manually created user feedback and sends it to Sentry.
   *
   * @param userFeedback The user feedback to send to Sentry.
   */
  void captureUserFeedback(@NotNull UserFeedback userFeedback);

  /** Starts a new session. If there's a running session, it ends it before starting the new one. */
  void startSession();

  /** Ends the current session */
  void endSession();

  /** Flushes out the queue for up to timeout seconds and disable the Hub. */
  void close();

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Object hint);

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  default void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   */
  default void addBreadcrumb(@NotNull String message) {
    addBreadcrumb(new Breadcrumb(message));
  }

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param message rendered as text and the whitespace is preserved.
   * @param category Categories are dotted strings that indicate what the crumb is or where it comes
   *     from.
   */
  default void addBreadcrumb(@NotNull String message, @NotNull String category) {
    Breadcrumb breadcrumb = new Breadcrumb(message);
    breadcrumb.setCategory(category);
    addBreadcrumb(breadcrumb);
  }

  /**
   * Sets the level of all events sent within current Scope
   *
   * @param level the Sentry level
   */
  void setLevel(@Nullable SentryLevel level);

  /**
   * Sets the name of the current transaction to the current Scope.
   *
   * @param transaction the transaction
   */
  void setTransaction(@Nullable String transaction);

  /**
   * Shallow merges user configuration (email, username, etc) to the current Scope.
   *
   * @param user the user
   */
  void setUser(@Nullable User user);

  /**
   * Sets the fingerprint to group specific events together to the current Scope.
   *
   * @param fingerprint the fingerprints
   */
  void setFingerprint(@NotNull List<String> fingerprint);

  /** Deletes current breadcrumbs from the current scope. */
  void clearBreadcrumbs();

  /**
   * Sets the tag to a string value to the current Scope, overwriting a potential previous value
   *
   * @param key the key
   * @param value the value
   */
  void setTag(@NotNull String key, @NotNull String value);

  /**
   * Removes the tag to a string value to the current Scope
   *
   * @param key the key
   */
  void removeTag(@NotNull String key);

  /**
   * Sets the extra key to an arbitrary value to the current Scope, overwriting a potential previous
   * value
   *
   * @param key the key
   * @param value the value
   */
  void setExtra(@NotNull String key, @NotNull String value);

  /**
   * Removes the extra key to an arbitrary value to the current Scope
   *
   * @param key the key
   */
  void removeExtra(@NotNull String key);

  /**
   * Last event id recorded in the current scope
   *
   * @return last SentryId
   */
  @NotNull
  SentryId getLastEventId();

  /** Pushes a new scope while inheriting the current scope's data. */
  void pushScope();

  /** Removes the first scope */
  void popScope();

  /**
   * Runs the callback with a new scope which gets dropped at the end
   *
   * @param callback the callback
   */
  void withScope(@NotNull ScopeCallback callback);

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  void configureScope(@NotNull ScopeCallback callback);

  /**
   * Binds a different client to the hub
   *
   * @param client the client.
   */
  void bindClient(@NotNull ISentryClient client);

  /**
   * Flushes events queued up, but keeps the Hub enabled. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);

  /**
   * Clones the Hub
   *
   * @return the cloned Hub
   */
  @NotNull
  IHub clone();

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param traceState the trace state
   * @param hint the hint
   * @return transaction's id
   */
  @ApiStatus.Internal
  @NotNull
  SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceState traceState,
      @Nullable Object hint);

  @ApiStatus.Internal
  @NotNull
  default SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable Object hint) {
    return captureTransaction(transaction, null, hint);
  }

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param traceState the trace state
   * @return transaction's id
   */
  @ApiStatus.Internal
  default @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable TraceState traceState) {
    return captureTransaction(transaction, traceState, null);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContexts the transaction contexts
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(@NotNull TransactionContext transactionContexts) {
    return startTransaction(transactionContexts, false);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContexts the transaction contexts
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts, boolean bindToScope) {
    return startTransaction(transactionContexts, null, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed sampling context the
   * decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param customSamplingContext the sampling context
   * @return created transaction.
   */
  default @NotNull ITransaction startTransaction(
      @NotNull String name,
      @NotNull String operation,
      @Nullable CustomSamplingContext customSamplingContext) {
    return startTransaction(name, operation, customSamplingContext, false);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed sampling context the
   * decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param customSamplingContext the sampling context
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction.
   */
  default @NotNull ITransaction startTransaction(
      @NotNull String name,
      @NotNull String operation,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope) {
    return startTransaction(
        new TransactionContext(name, operation), customSamplingContext, bindToScope);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed transaction and sampling
   * contexts the decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param transactionContexts the transaction context
   * @param customSamplingContext the sampling context
   * @return created transaction.
   */
  default @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext) {
    return startTransaction(transactionContexts, customSamplingContext, false);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed transaction and sampling
   * contexts the decision if transaction is sampled will be taken by {@link TracesSampler}.
   *
   * @param transactionContexts the transaction context
   * @param customSamplingContext the sampling context
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction.
   */
  @NotNull
  ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope);

  @ApiStatus.Internal
  @NotNull
  ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope,
      @Nullable Date startTimestamp);

  @ApiStatus.Internal
  @NotNull
  ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope,
      @Nullable Date startTimestamp,
      boolean waitForChildren,
      @Nullable TransactionFinishedCallback transactionFinishedCallback);

  /**
   * Creates a Transaction and returns the instance. Based on the {@link
   * SentryOptions#getTracesSampleRate()} the decision if transaction is sampled will be taken by
   * {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(
      final @NotNull String name, final @NotNull String operation) {
    return startTransaction(name, operation, null);
  }

  @ApiStatus.Internal
  default @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      @Nullable Date startTimestamp,
      boolean waitForChildren,
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    return startTransaction(
        new TransactionContext(name, operation),
        null,
        false,
        startTimestamp,
        waitForChildren,
        transactionFinishedCallback);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the {@link
   * SentryOptions#getTracesSampleRate()} the decision if transaction is sampled will be taken by
   * {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param bindToScope if transaction should be bound to scope
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(
      final @NotNull String name, final @NotNull String operation, final boolean bindToScope) {
    return startTransaction(name, operation, (CustomSamplingContext) null, bindToScope);
  }

  /**
   * Returns trace header of active transaction or {@code null} if no transaction is active.
   *
   * @return trace header or null
   */
  @Nullable
  SentryTraceHeader traceHeaders();

  /**
   * Associates {@link ISpan} and the transaction name with the {@link Throwable}. Used to determine
   * in which trace the exception has been thrown in framework integrations.
   *
   * @param throwable the throwable
   * @param span the span context
   * @param transactionName the transaction name
   */
  @ApiStatus.Internal
  void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName);

  /**
   * Gets the current active transaction or span.
   *
   * @return the active span or null when no active transaction is running
   */
  @Nullable
  ISpan getSpan();

  /**
   * Gets the {@link SentryOptions} attached to current scope.
   *
   * @return the options attached to current scope.
   */
  @NotNull
  SentryOptions getOptions();

  /**
   * Returns if the App has crashed (Process has terminated) during the last run. It only returns
   * true or false if offline caching {{@link SentryOptions#getCacheDirPath()} } is set with a valid
   * dir.
   *
   * <p>If the call to this method is early in the App lifecycle and the SDK could not check if the
   * App has crashed in the background, the check is gonna do IO in the calling thread.
   *
   * @return true if App has crashed, false otherwise, and null if not evaluated yet
   */
  @Nullable
  Boolean isCrashedLastRun();
}
