package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IScopes {

  /**
   * Check if Sentry is enabled/active.
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
  SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint);

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return captureEvent(event, new Hint());
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, final @NotNull ScopeCallback callback) {
    return captureEvent(event, new Hint(), callback);
  }

  /**
   * Captures the event.
   *
   * @param event the event
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureEvent(
      final @NotNull SentryEvent event,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback);

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
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @NotNull ScopeCallback callback);

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull ScopeCallback callback) {
    return captureMessage(message, SentryLevel.INFO, callback);
  }

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope) {
    return captureEnvelope(envelope, new Hint());
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint);

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return captureException(throwable, new Hint());
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  default @NotNull SentryId captureException(
      @NotNull Throwable throwable, final @NotNull ScopeCallback callback) {
    return captureException(throwable, new Hint(), callback);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @param callback The callback to configure the scope for a single invocation.
   * @return The Id (SentryId object) of the event
   */
  @NotNull
  SentryId captureException(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback);

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

  /** Flushes out the queue for up to timeout seconds and disable the Scopes. */
  void close();

  /**
   * Flushes out the queue for up to timeout seconds and disable the Scopes.
   *
   * @param isRestarting if true, avoids locking the main thread when finishing the queue.
   */
  void close(boolean isRestarting);

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   * @param hint SDK specific but provides high level information about the origin of the event
   */
  void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint);

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  void addBreadcrumb(@NotNull Breadcrumb breadcrumb);

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
  @NotNull
  ISentryLifecycleToken pushScope();

  @NotNull
  ISentryLifecycleToken pushIsolationScope();

  /**
   * Removes the first scope and restores its parent.
   *
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Deprecated
  void popScope();

  /**
   * Runs the callback with a new current scope which gets dropped at the end.
   *
   * <p>If you're using the Sentry SDK in globalHubMode (defaults to true on Android) {@link
   * Sentry#init(Sentry.OptionsConfiguration, boolean)} calling withScope is discouraged, as scope
   * changes may be dropped when executed in parallel. Use {@link
   * IScopes#configureScope(ScopeCallback)} instead.
   *
   * @param callback the callback
   */
  void withScope(@NotNull ScopeCallback callback);

  /**
   * Runs the callback with a new isolation scope which gets dropped at the end. Current scope is
   * also forked.
   *
   * <p>If you're using the Sentry SDK in globalHubMode (defaults to true on Android) {@link
   * Sentry#init(Sentry.OptionsConfiguration, boolean)} calling withScope is discouraged, as scope
   * changes may be dropped when executed in parallel. Use {@link IScopes#configureScope(ScopeType,
   * ScopeCallback)} instead.
   *
   * @param callback the callback
   */
  void withIsolationScope(@NotNull ScopeCallback callback);

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  default void configureScope(@NotNull ScopeCallback callback) {
    configureScope(null, callback);
  }

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback);

  /**
   * Binds a different client to the scopes
   *
   * @param client the client.
   */
  void bindClient(@NotNull ISentryClient client);

  /**
   * Whether the transport is healthy.
   *
   * @return true if the transport is healthy
   */
  boolean isHealthy();

  /**
   * Flushes events queued up, but keeps the scopes enabled. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);

  /**
   * Clones the Hub
   *
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   * @return the cloned Hub
   */
  @NotNull
  @Deprecated
  IHub clone();

  /**
   * Creates a fork of both current and isolation scope from current scopes.
   *
   * @param creator debug information to see why scopes where forked
   * @return forked Scopes
   */
  @NotNull
  IScopes forkedScopes(final @NotNull String creator);

  /**
   * Creates a fork of current scope without forking isolation scope.
   *
   * @param creator debug information to see why scopes where forked
   * @return forked Scopes
   */
  @NotNull
  IScopes forkedCurrentScope(final @NotNull String creator);

  /**
   * Creates a fork of both current and isolation scope from root scopes.
   *
   * @param creator debug information to see why scopes where forked
   * @return forked Scopes
   */
  @NotNull
  IScopes forkedRootScopes(final @NotNull String creator);

  /**
   * Stores this Scopes in store, making it the current one that is used by static API.
   *
   * @return a token you should call .close() on when you're done.
   */
  @NotNull
  ISentryLifecycleToken makeCurrent();

  /**
   * Returns the current scope of this Scopes.
   *
   * @return scope
   */
  @ApiStatus.Internal
  @NotNull
  IScope getScope();

  /**
   * Returns the isolation scope of this Scopes.
   *
   * @return isolation scope
   */
  @ApiStatus.Internal
  @NotNull
  IScope getIsolationScope();

  /**
   * Returns the global scope.
   *
   * @return global scope
   */
  @ApiStatus.Internal
  @NotNull
  IScope getGlobalScope();

  /**
   * Returns the parent of this Scopes instance or null, if it does not have a parent. The parent is
   * the Scopes instance this instance was forked from.
   *
   * @return parent Scopes or null
   */
  @ApiStatus.Internal
  @Nullable
  IScopes getParentScopes();

  /**
   * Checks whether this Scopes instance is direct or indirect parent of the other Scopes instance.
   *
   * @param otherScopes Scopes instance that could be a direct or indirect child.
   * @return true if this Scopes instance is a direct or indirect parent of the other Scopes.
   */
  @ApiStatus.Internal
  boolean isAncestorOf(final @Nullable IScopes otherScopes);

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param traceContext the trace context
   * @param hint the hints
   * @param profilingTraceData the profiling trace data
   * @return transaction's id
   */
  @ApiStatus.Internal
  @NotNull
  SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData);

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param traceContext the trace context
   * @param hint the hints
   * @return transaction's id
   */
  @ApiStatus.Internal
  @NotNull
  default SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint) {
    return captureTransaction(transaction, traceContext, hint, null);
  }

  @ApiStatus.Internal
  @NotNull
  default SentryId captureTransaction(@NotNull SentryTransaction transaction, @Nullable Hint hint) {
    return captureTransaction(transaction, null, hint);
  }

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param traceContext the trace context
   * @return transaction's id
   */
  @ApiStatus.Internal
  default @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable TraceContext traceContext) {
    return captureTransaction(transaction, traceContext, null);
  }

  /**
   * Creates a Transaction and returns the instance.
   *
   * @param transactionContexts the transaction contexts
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(@NotNull TransactionContext transactionContexts) {
    return startTransaction(transactionContexts, new TransactionOptions());
  }

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
    return startTransaction(name, operation, new TransactionOptions());
  }

  /**
   * Creates a Transaction and returns the instance. Based on the {@link
   * SentryOptions#getTracesSampleRate()} the decision if transaction is sampled will be taken by
   * {@link TracesSampler}.
   *
   * @param name the transaction name
   * @param operation the operation
   * @param transactionOptions the transaction options
   * @return created transaction
   */
  default @NotNull ITransaction startTransaction(
      final @NotNull String name,
      final @NotNull String operation,
      final @NotNull TransactionOptions transactionOptions) {
    return startTransaction(new TransactionContext(name, operation), transactionOptions);
  }

  /**
   * Creates a Transaction and returns the instance. Based on the passed transaction context and
   * transaction options the decision if transaction is sampled will be taken by {@link
   * TracesSampler}.
   *
   * @param transactionContext the transaction context
   * @param transactionOptions the transaction options
   * @return created transaction.
   */
  @NotNull
  ITransaction startTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions);

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

  @ApiStatus.Internal
  void setActiveSpan(@Nullable ISpan span);

  /**
   * Returns the transaction.
   *
   * @return the transaction or null when no active transaction is running.
   */
  @ApiStatus.Internal
  @Nullable
  ITransaction getTransaction();

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

  /**
   * Report a screen has been fully loaded. That means all data needed by the UI was loaded. If
   * time-to-full-display tracing {{@link SentryOptions#isEnableTimeToFullDisplayTracing()} } is
   * disabled this call is ignored.
   *
   * <p>This method is safe to be called multiple times. If the time-to-full-display span is already
   * finished, this call will be ignored.
   */
  void reportFullyDisplayed();

  /**
   * Continue a trace based on HTTP header values. If no "sentry-trace" header is provided a random
   * trace ID and span ID is created.
   *
   * @param sentryTrace "sentry-trace" header
   * @param baggageHeaders "baggage" headers
   * @return a transaction context for starting a transaction or null if performance is disabled
   */
  @Nullable
  TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders);

  /**
   * Returns the "sentry-trace" header that allows tracing across services. Can also be used in
   * &lt;meta&gt; HTML tags. Also see {@link IScopes#getBaggage()}.
   *
   * @return sentry trace header or null
   */
  @Nullable
  SentryTraceHeader getTraceparent();

  /**
   * Returns the "baggage" header that allows tracing across services. Can also be used in
   * &lt;meta&gt; HTML tags. Also see {@link IScopes#getTraceparent()}.
   *
   * @return baggage header or null
   */
  @Nullable
  BaggageHeader getBaggage();

  @ApiStatus.Experimental
  @NotNull
  SentryId captureCheckIn(final @NotNull CheckIn checkIn);

  @ApiStatus.Internal
  @Nullable
  RateLimiter getRateLimiter();

  default boolean isNoOp() {
    return false;
  }

  @NotNull
  SentryId captureReplay(@NotNull SentryReplayEvent replay, @Nullable Hint hint);
}
