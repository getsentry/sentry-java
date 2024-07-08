package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IScope {
  @Nullable
  SentryLevel getLevel();

  /**
   * Sets the Scope's SentryLevel Level from scope exceptionally take precedence over the event
   *
   * @param level the SentryLevel
   */
  void setLevel(final @Nullable SentryLevel level);

  /**
   * Returns the Scope's transaction name.
   *
   * @return the transaction
   */
  @Nullable
  String getTransactionName();

  /**
   * Sets the Scope's transaction.
   *
   * @param transaction the transaction
   */
  void setTransaction(final @NotNull String transaction);

  /**
   * Returns current active Span or Transaction.
   *
   * @return current active Span or Transaction or null if transaction has not been set.
   */
  @Nullable
  ISpan getSpan();

  /**
   * Sets the current active transaction
   *
   * @param transaction the transaction
   */
  void setTransaction(final @Nullable ITransaction transaction);

  /**
   * Returns the Scope's user
   *
   * @return the user
   */
  @Nullable
  User getUser();

  /**
   * Sets the Scope's user
   *
   * @param user the user
   */
  void setUser(final @Nullable User user);

  /**
   * Returns the Scope's current screen, previously set by {@link IScope#setScreen(String)}
   *
   * @return the name of the screen
   */
  @ApiStatus.Internal
  @Nullable
  String getScreen();

  /**
   * Sets the Scope's current screen
   *
   * @param screen the name of the screen
   */
  @ApiStatus.Internal
  void setScreen(final @Nullable String screen);

  /**
   * Returns the Scope's request
   *
   * @return the request
   */
  @Nullable
  Request getRequest();

  /**
   * Sets the Scope's request
   *
   * @param request the request
   */
  void setRequest(final @Nullable Request request);

  /**
   * Returns the Scope's fingerprint list
   *
   * @return the fingerprint list
   */
  @ApiStatus.Internal
  @NotNull
  List<String> getFingerprint();

  /**
   * Sets the Scope's fingerprint list
   *
   * @param fingerprint the fingerprint list
   */
  void setFingerprint(final @NotNull List<String> fingerprint);

  /**
   * Returns the Scope's breadcrumbs queue
   *
   * @return the breadcrumbs queue
   */
  @ApiStatus.Internal
  @NotNull
  Queue<Breadcrumb> getBreadcrumbs();

  /**
   * Adds a breadcrumb to the breadcrumbs queue. It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   * @param hint the hint
   */
  void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint);

  /**
   * Adds a breadcrumb to the breadcrumbs queue It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   */
  void addBreadcrumb(final @NotNull Breadcrumb breadcrumb);

  /** Clear all the breadcrumbs */
  void clearBreadcrumbs();

  /** Clears the transaction. */
  void clearTransaction();

  /**
   * Returns active transaction or null if there is no active transaction.
   *
   * @return the transaction
   */
  @Nullable
  ITransaction getTransaction();

  /** Resets the Scope to its default state */
  void clear();

  /**
   * Returns the Scope's tags
   *
   * @return the tags map
   */
  @ApiStatus.Internal
  @SuppressWarnings("NullAway") // tags are never null
  @NotNull
  Map<String, String> getTags();

  /**
   * Sets a tag to Scope's tags
   *
   * @param key the key
   * @param value the value
   */
  void setTag(final @NotNull String key, final @NotNull String value);

  /**
   * Removes a tag from the Scope's tags
   *
   * @param key the key
   */
  void removeTag(final @NotNull String key);

  /**
   * Returns the Scope's extra map
   *
   * @return the extra map
   */
  @ApiStatus.Internal
  @NotNull
  Map<String, Object> getExtras();

  /**
   * Sets an extra to the Scope's extra map
   *
   * @param key the key
   * @param value the value
   */
  void setExtra(final @NotNull String key, final @NotNull String value);

  /**
   * Removes an extra from the Scope's extras
   *
   * @param key the key
   */
  void removeExtra(final @NotNull String key);

  /**
   * Returns the Scope's contexts
   *
   * @return the contexts
   */
  @NotNull
  Contexts getContexts();

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Object value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Boolean value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull String value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Number value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Collection<?> value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Object[] value);

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  void setContexts(final @NotNull String key, final @NotNull Character value);

  /**
   * Removes a value from the Scope's contexts
   *
   * @param key the Key
   */
  void removeContexts(final @NotNull String key);

  /**
   * Returns the Scopes's attachments
   *
   * @return the attachments
   */
  @NotNull
  List<Attachment> getAttachments();

  /**
   * Adds an attachment to the Scope's list of attachments. The SDK adds the attachment to every
   * event and transaction sent to Sentry.
   *
   * @param attachment The attachment to add to the Scope's list of attachments.
   */
  void addAttachment(final @NotNull Attachment attachment);

  /** Clear all attachments. */
  void clearAttachments();

  /**
   * Returns the Scope's event processors
   *
   * @return the event processors list
   */
  @NotNull
  List<EventProcessor> getEventProcessors();

  /**
   * Adds an event processor to the Scope's event processors list
   *
   * @param eventProcessor the event processor
   */
  void addEventProcessor(final @NotNull EventProcessor eventProcessor);

  /**
   * Callback to do atomic operations on session
   *
   * @param sessionCallback the IWithSession callback
   * @return a clone of the Session after executing the callback and mutating the session
   */
  @Nullable
  Session withSession(final @NotNull Scope.IWithSession sessionCallback);

  /**
   * Returns a previous session (now closed) bound to this scope together with the newly created one
   *
   * @return the SessionPair with the previous closed session if exists and the current session
   */
  @Nullable
  Scope.SessionPair startSession();

  /**
   * Ends a session, unbinds it from the scope and returns it.
   *
   * @return the previous session
   */
  @Nullable
  Session endSession();

  /**
   * Mutates the current transaction atomically
   *
   * @param callback the IWithTransaction callback
   */
  @ApiStatus.Internal
  void withTransaction(final @NotNull Scope.IWithTransaction callback);

  @NotNull
  SentryOptions getOptions();

  @ApiStatus.Internal
  @Nullable
  Session getSession();

  @ApiStatus.Internal
  void clearSession();

  @ApiStatus.Internal
  void setPropagationContext(final @NotNull PropagationContext propagationContext);

  @ApiStatus.Internal
  @NotNull
  PropagationContext getPropagationContext();

  @ApiStatus.Internal
  @NotNull
  PropagationContext withPropagationContext(final @NotNull Scope.IWithPropagationContext callback);

  /**
   * Clones the Scope
   *
   * @return the cloned Scope
   */
  @NotNull
  IScope clone();
}
