package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
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
  SentryId captureEvent(SentryEvent event, @Nullable Object hint);

  /**
   * Captures the event.
   *
   * @param event the event
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureEvent(SentryEvent event) {
    return captureEvent(event, null);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureMessage(String message) {
    return captureMessage(message, SentryLevel.INFO);
  }

  /**
   * Captures the message.
   *
   * @param message The message to send.
   * @param level The message level.
   * @return The Id (SentryId object) of the event
   */
  SentryId captureMessage(String message, SentryLevel level);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  SentryId captureEnvelope(SentryEnvelope envelope, @Nullable Object hint);

  /**
   * Captures an envelope.
   *
   * @param envelope the SentryEnvelope to send.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureEnvelope(SentryEnvelope envelope) {
    return captureEnvelope(envelope, null);
  }

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @param hint SDK specific but provides high level information about the origin of the event
   * @return The Id (SentryId object) of the event
   */
  SentryId captureException(Throwable throwable, @Nullable Object hint);

  /**
   * Captures the exception.
   *
   * @param throwable The exception.
   * @return The Id (SentryId object) of the event
   */
  default SentryId captureException(Throwable throwable) {
    return captureException(throwable, null);
  }

  /**
   * Captures a manually created user feedback and sends it to Sentry.
   *
   * @param userFeedback The user feedback to send to Sentry.
   */
  void captureUserFeedback(UserFeedback userFeedback);

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
  void addBreadcrumb(Breadcrumb breadcrumb, @Nullable Object hint);

  /**
   * Adds a breadcrumb to the current Scope
   *
   * @param breadcrumb the breadcrumb
   */
  default void addBreadcrumb(Breadcrumb breadcrumb) {
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
  void setLevel(SentryLevel level);

  /**
   * Sets the name of the current transaction to the current Scope.
   *
   * @param transaction the transaction
   */
  void setTransaction(String transaction);

  /**
   * Shallow merges user configuration (email, username, etc) to the current Scope.
   *
   * @param user the user
   */
  void setUser(User user);

  /**
   * Sets the fingerprint to group specific events together to the current Scope.
   *
   * @param fingerprint the fingerprints
   */
  void setFingerprint(List<String> fingerprint);

  /** Deletes current breadcrumbs from the current scope. */
  void clearBreadcrumbs();

  /**
   * Sets the tag to a string value to the current Scope, overwriting a potential previous value
   *
   * @param key the key
   * @param value the value
   */
  void setTag(String key, String value);

  /**
   * Removes the tag to a string value to the current Scope
   *
   * @param key the key
   */
  void removeTag(String key);

  /**
   * Sets the extra key to an arbitrary value to the current Scope, overwriting a potential previous
   * value
   *
   * @param key the key
   * @param value the value
   */
  void setExtra(String key, String value);

  /**
   * Removes the extra key to an arbitrary value to the current Scope
   *
   * @param key the key
   */
  void removeExtra(String key);

  /**
   * Last event id recorded in the current scope
   *
   * @return last SentryId
   */
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
  void withScope(ScopeCallback callback);

  /**
   * Configures the scope through the callback.
   *
   * @param callback The configure scope callback.
   */
  void configureScope(ScopeCallback callback);

  /**
   * Binds a different client to the hub
   *
   * @param client the client.
   */
  void bindClient(ISentryClient client);

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
  IHub clone();

  /**
   * Captures the transaction and enqueues it for sending to Sentry server.
   *
   * @param transaction the transaction
   * @param hint the hint
   * @return transaction's id
   */
  @ApiStatus.Internal
  SentryId captureTransaction(Transaction transaction, Object hint);

  /**
   * Creates a Transaction bound to the current hub and returns the instance.
   *
   * @param name the transaction name
   * @param transactionContexts the transaction contexts
   * @return created transaction
   */
  Transaction startTransaction(String name, TransactionContexts transactionContexts);

  /**
   * Creates a Transaction bound to the current hub and returns the instance.
   *
   * @param name the transaction name
   * @return created transaction
   */
  default Transaction startTransaction(String name) {
    return startTransaction(name, new TransactionContexts());
  }
}
