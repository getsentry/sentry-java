package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.protocol.User;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Scope data to be sent with the event */
public final class Scope {

  /** Scope's SentryLevel */
  private @Nullable SentryLevel level;

  /** Scope's {@link ITransaction}. */
  private @Nullable ITransaction transaction;

  /** Scope's transaction name. Used when using error reporting without the performance feature. */
  private @Nullable String transactionName;

  /** Scope's user */
  private @Nullable User user;

  /** Scope's request */
  private @Nullable Request request;

  /** Scope's fingerprint */
  private @NotNull List<String> fingerprint = new ArrayList<>();

  /** Scope's breadcrumb queue */
  private @NotNull Queue<Breadcrumb> breadcrumbs;

  /** Scope's tags */
  private @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();

  /** Scope's extras */
  private @NotNull Map<String, @NotNull Object> extra = new ConcurrentHashMap<>();

  /** Scope's event processor list */
  private @NotNull List<EventProcessor> eventProcessors = new CopyOnWriteArrayList<>();

  /** Scope's SentryOptions */
  private final @NotNull SentryOptions options;

  // TODO Consider: Scope clone doesn't clone sessions

  /** Scope's current session */
  private volatile @Nullable Session session;

  /** Session lock, Ops should be atomic */
  private final @NotNull Object sessionLock = new Object();

  /** Transaction lock, Ops should be atomic */
  private final @NotNull Object transactionLock = new Object();

  /** Scope's contexts */
  private @NotNull Contexts contexts = new Contexts();

  /** Scope's attachments */
  private @NotNull List<Attachment> attachments = new CopyOnWriteArrayList<>();

  /**
   * Scope's ctor
   *
   * @param options the options
   */
  public Scope(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required.");
    this.breadcrumbs = createBreadcrumbsList(this.options.getMaxBreadcrumbs());
  }

  Scope(final @NotNull Scope scope) {
    this.transaction = scope.transaction;
    this.transactionName = scope.transactionName;
    this.session = scope.session;
    this.options = scope.options;
    this.level = scope.level;

    final User userRef = scope.user;
    this.user = userRef != null ? new User(userRef) : null;

    final Request requestRef = scope.request;
    this.request = requestRef != null ? new Request(requestRef) : null;

    this.fingerprint = new ArrayList<>(scope.fingerprint);
    this.eventProcessors = new CopyOnWriteArrayList<>(scope.eventProcessors);

    final Breadcrumb[] breadcrumbsRef = scope.breadcrumbs.toArray(new Breadcrumb[0]);

    Queue<Breadcrumb> breadcrumbsClone = createBreadcrumbsList(scope.options.getMaxBreadcrumbs());

    for (Breadcrumb item : breadcrumbsRef) {
      final Breadcrumb breadcrumbClone = new Breadcrumb(item);
      breadcrumbsClone.add(breadcrumbClone);
    }
    this.breadcrumbs = breadcrumbsClone;

    final Map<String, String> tagsRef = scope.tags;

    final Map<String, @NotNull String> tagsClone = new ConcurrentHashMap<>();

    for (Map.Entry<String, String> item : tagsRef.entrySet()) {
      if (item != null) {
        tagsClone.put(item.getKey(), item.getValue()); // shallow copy
      }
    }

    this.tags = tagsClone;

    final Map<String, Object> extraRef = scope.extra;

    Map<String, @NotNull Object> extraClone = new ConcurrentHashMap<>();

    for (Map.Entry<String, Object> item : extraRef.entrySet()) {
      if (item != null) {
        extraClone.put(item.getKey(), item.getValue()); // shallow copy
      }
    }

    this.extra = extraClone;

    this.contexts = new Contexts(scope.contexts);

    this.attachments = new CopyOnWriteArrayList<>(scope.attachments);
  }

  /**
   * Returns the Scope's SentryLevel
   *
   * @return the SentryLevel
   */
  public @Nullable SentryLevel getLevel() {
    return level;
  }

  /**
   * Sets the Scope's SentryLevel Level from scope exceptionally take precedence over the event
   *
   * @param level the SentryLevel
   */
  public void setLevel(final @Nullable SentryLevel level) {
    this.level = level;
  }

  /**
   * Returns the Scope's transaction name.
   *
   * @return the transaction
   */
  public @Nullable String getTransactionName() {
    final ITransaction tx = this.transaction;
    return tx != null ? tx.getName() : transactionName;
  }

  /**
   * Sets the Scope's transaction.
   *
   * @param transaction the transaction
   */
  public void setTransaction(final @NotNull String transaction) {
    if (transaction != null) {
      final ITransaction tx = this.transaction;
      if (tx != null) {
        tx.setName(transaction, TransactionNameSource.CUSTOM);
      }
      this.transactionName = transaction;
    } else {
      options.getLogger().log(SentryLevel.WARNING, "Transaction cannot be null");
    }
  }

  /**
   * Returns current active Span or Transaction.
   *
   * @return current active Span or Transaction or null if transaction has not been set.
   */
  @Nullable
  public ISpan getSpan() {
    final ITransaction tx = transaction;
    if (tx != null) {
      final Span span = tx.getLatestActiveSpan();

      if (span != null) {
        return span;
      }
    }
    return tx;
  }

  /**
   * Sets the current active transaction
   *
   * @param transaction the transaction
   */
  public void setTransaction(final @Nullable ITransaction transaction) {
    synchronized (transactionLock) {
      this.transaction = transaction;
    }
  }

  /**
   * Returns the Scope's user
   *
   * @return the user
   */
  public @Nullable User getUser() {
    return user;
  }

  /**
   * Sets the Scope's user
   *
   * @param user the user
   */
  public void setUser(final @Nullable User user) {
    this.user = user;

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setUser(user);
      }
    }
  }

  /**
   * Returns the Scope's request
   *
   * @return the request
   */
  public @Nullable Request getRequest() {
    return request;
  }

  /**
   * Sets the Scope's request
   *
   * @param request the request
   */
  public void setRequest(final @Nullable Request request) {
    this.request = request;
  }

  /**
   * Returns the Scope's fingerprint list
   *
   * @return the fingerprint list
   */
  @NotNull
  List<String> getFingerprint() {
    return fingerprint;
  }

  /**
   * Sets the Scope's fingerprint list
   *
   * @param fingerprint the fingerprint list
   */
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (fingerprint == null) {
      return;
    }
    this.fingerprint = new ArrayList<>(fingerprint);
  }

  /**
   * Returns the Scope's breadcrumbs queue
   *
   * @return the breadcrumbs queue
   */
  @NotNull
  Queue<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  /**
   * Executes the BeforeBreadcrumb callback
   *
   * @param callback the BeforeBreadcrumb callback
   * @param breadcrumb the breadcrumb
   * @param hint the hints
   * @return the mutated breadcrumb or null if dropped
   */
  private @Nullable Breadcrumb executeBeforeBreadcrumb(
      final @NotNull SentryOptions.BeforeBreadcrumbCallback callback,
      @NotNull Breadcrumb breadcrumb,
      final @NotNull Hint hint) {
    try {
      breadcrumb = callback.execute(breadcrumb, hint);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "The BeforeBreadcrumbCallback callback threw an exception. Exception details will be added to the breadcrumb.",
              e);

      if (e.getMessage() != null) {
        breadcrumb.setData("sentry:message", e.getMessage());
      }
    }
    return breadcrumb;
  }

  /**
   * Adds a breadcrumb to the breadcrumbs queue It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   * @param hint the hint
   */
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    if (breadcrumb == null) {
      return;
    }
    if (hint == null) {
      hint = new Hint();
    }

    SentryOptions.BeforeBreadcrumbCallback callback = options.getBeforeBreadcrumb();
    if (callback != null) {
      breadcrumb = executeBeforeBreadcrumb(callback, breadcrumb, hint);
    }
    if (breadcrumb != null) {
      this.breadcrumbs.add(breadcrumb);

      if (options.isEnableScopeSync()) {
        for (final IScopeObserver observer : options.getScopeObservers()) {
          observer.addBreadcrumb(breadcrumb);
        }
      }
    } else {
      options.getLogger().log(SentryLevel.INFO, "Breadcrumb was dropped by beforeBreadcrumb");
    }
  }

  /**
   * Adds a breadcrumb to the breadcrumbs queue It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   */
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  /** Clear all the breadcrumbs */
  public void clearBreadcrumbs() {
    breadcrumbs.clear();
  }

  /** Clears the transaction. */
  public void clearTransaction() {
    synchronized (transactionLock) {
      transaction = null;
    }
    transactionName = null;
  }

  /**
   * Returns active transaction or null if there is no active transaction.
   *
   * @return the transaction
   */
  @Nullable
  public ITransaction getTransaction() {
    return this.transaction;
  }

  /** Resets the Scope to its default state */
  public void clear() {
    level = null;
    user = null;
    request = null;
    fingerprint.clear();
    clearBreadcrumbs();
    tags.clear();
    extra.clear();
    eventProcessors.clear();
    clearTransaction();
    clearAttachments();
  }

  /**
   * Returns the Scope's tags
   *
   * @return the tags map
   */
  @ApiStatus.Internal
  @SuppressWarnings("NullAway") // tags are never null
  public @NotNull Map<String, String> getTags() {
    return CollectionUtils.newConcurrentHashMap(tags);
  }

  /**
   * Sets a tag to Scope's tags
   *
   * @param key the key
   * @param value the value
   */
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.tags.put(key, value);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setTag(key, value);
      }
    }
  }

  /**
   * Removes a tag from the Scope's tags
   *
   * @param key the key
   */
  public void removeTag(final @NotNull String key) {
    this.tags.remove(key);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.removeTag(key);
      }
    }
  }

  /**
   * Returns the Scope's extra map
   *
   * @return the extra map
   */
  @NotNull
  Map<String, Object> getExtras() {
    return extra;
  }

  /**
   * Sets an extra to the Scope's extra map
   *
   * @param key the key
   * @param value the value
   */
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    this.extra.put(key, value);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setExtra(key, value);
      }
    }
  }

  /**
   * Removes an extra from the Scope's extras
   *
   * @param key the key
   */
  public void removeExtra(final @NotNull String key) {
    this.extra.remove(key);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.removeExtra(key);
      }
    }
  }

  /**
   * Returns the Scope's contexts
   *
   * @return the contexts
   */
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Object value) {
    this.contexts.put(key, value);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Boolean value) {
    final Map<String, Boolean> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull String value) {
    final Map<String, String> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Number value) {
    final Map<String, Number> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Collection<?> value) {
    final Map<String, Collection<?>> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Object[] value) {
    final Map<String, Object[]> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Character value) {
    final Map<String, Character> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Removes a value from the Scope's contexts
   *
   * @param key the Key
   */
  public void removeContexts(final @NotNull String key) {
    contexts.remove(key);
  }

  /**
   * Returns the Scopes's attachments
   *
   * @return the attachments
   */
  @NotNull
  List<Attachment> getAttachments() {
    return new CopyOnWriteArrayList<>(attachments);
  }

  /**
   * Adds an attachment to the Scope's list of attachments. The SDK adds the attachment to every
   * event and transaction sent to Sentry.
   *
   * @param attachment The attachment to add to the Scope's list of attachments.
   */
  public void addAttachment(final @NotNull Attachment attachment) {
    attachments.add(attachment);
  }

  /** Clear all attachments. */
  public void clearAttachments() {
    attachments.clear();
  }

  /**
   * Creates a breadcrumb list with the max number of breadcrumbs
   *
   * @param maxBreadcrumb the max number of breadcrumbs
   * @return the breadcrumbs queue
   */
  private @NotNull Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb));
  }

  /**
   * Returns the Scope's event processors
   *
   * @return the event processors list
   */
  @NotNull
  List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  /**
   * Adds an event processor to the Scope's event processors list
   *
   * @param eventProcessor the event processor
   */
  public void addEventProcessor(final @NotNull EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  /**
   * Callback to do atomic operations on session
   *
   * @param sessionCallback the IWithSession callback
   * @return a clone of the Session after executing the callback and mutating the session
   */
  @Nullable
  Session withSession(final @NotNull IWithSession sessionCallback) {
    Session cloneSession = null;
    synchronized (sessionLock) {
      sessionCallback.accept(session);

      if (session != null) {
        cloneSession = session.clone();
      }
    }
    return cloneSession;
  }

  /** the IWithSession callback */
  interface IWithSession {

    /**
     * The accept method of the callback
     *
     * @param session the current session or null if none exists
     */
    void accept(@Nullable Session session);
  }

  /**
   * Returns a previous session (now closed) bound to this scope together with the newly created one
   *
   * @return the SessionPair with the previous closed session if exists and the current session
   */
  @Nullable
  SessionPair startSession() {
    Session previousSession;
    SessionPair pair = null;
    synchronized (sessionLock) {
      if (session != null) {
        // Assumes session will NOT flush itself (Not passing any hub to it)
        session.end();
      }
      previousSession = session;

      if (options.getRelease() != null) {
        session =
            new Session(
                options.getDistinctId(), user, options.getEnvironment(), options.getRelease());

        final Session previousClone = previousSession != null ? previousSession.clone() : null;
        pair = new SessionPair(session.clone(), previousClone);
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Release is not set on SentryOptions. Session could not be started");
      }
    }
    return pair;
  }

  /** The SessionPair class */
  static final class SessionPair {

    /** the previous session if exists */
    private final @Nullable Session previous;

    /** The current Session */
    private final @NotNull Session current;

    /**
     * The SessionPar ctor
     *
     * @param current the current session
     * @param previous the previous sessions if exists or null
     */
    public SessionPair(final @NotNull Session current, final @Nullable Session previous) {
      this.current = current;
      this.previous = previous;
    }

    /**
     * REturns the previous session
     *
     * @return the previous sessions if exists or null
     */
    public @Nullable Session getPrevious() {
      return previous;
    }

    /**
     * Returns the current session
     *
     * @return the current session
     */
    public @NotNull Session getCurrent() {
      return current;
    }
  }

  /**
   * Ends a session, unbinds it from the scope and returns it.
   *
   * @return the previous session
   */
  @Nullable
  Session endSession() {
    Session previousSession = null;
    synchronized (sessionLock) {
      if (session != null) {
        session.end();
        previousSession = session.clone();
        session = null;
      }
    }
    return previousSession;
  }

  /**
   * Mutates the current transaction atomically
   *
   * @param callback the IWithTransaction callback
   */
  @ApiStatus.Internal
  public void withTransaction(final @NotNull IWithTransaction callback) {
    synchronized (transactionLock) {
      callback.accept(transaction);
    }
  }

  @ApiStatus.Internal
  public @Nullable Session getSession() {
    return session;
  }

  /** the IWithTransaction callback */
  @ApiStatus.Internal
  public interface IWithTransaction {

    /**
     * The accept method of the callback
     *
     * @param transaction the current transaction or null if none exists
     */
    void accept(@Nullable ITransaction transaction);
  }
}
