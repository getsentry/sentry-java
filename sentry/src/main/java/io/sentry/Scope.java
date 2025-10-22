package io.sentry;

import io.sentry.featureflags.FeatureFlagBuffer;
import io.sentry.featureflags.IFeatureFlagBuffer;
import io.sentry.internal.eventprocessor.EventProcessorAndOrder;
import io.sentry.protocol.App;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.FeatureFlags;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.protocol.User;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.CollectionUtils;
import io.sentry.util.EventProcessorUtils;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.Objects;
import io.sentry.util.Pair;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Scope data to be sent with the event */
public final class Scope implements IScope {

  private volatile @NotNull SentryId lastEventId;

  /** Scope's SentryLevel */
  private @Nullable SentryLevel level;

  /** Scope's {@link ITransaction}. */
  private @Nullable ITransaction transaction;

  private @NotNull WeakReference<ISpan> activeSpan = new WeakReference<>(null);

  /** Scope's transaction name. Used when using error reporting without the performance feature. */
  private @Nullable String transactionName;

  /** Scope's user */
  private @Nullable User user;

  /** Scope's screen */
  private @Nullable String screen;

  /** Scope's request */
  private @Nullable Request request;

  /** Scope's fingerprint */
  private @NotNull List<String> fingerprint = new ArrayList<>();

  /** Scope's breadcrumb queue */
  private volatile @NotNull Queue<Breadcrumb> breadcrumbs;

  /** Scope's tags */
  private @NotNull Map<String, @NotNull String> tags = new ConcurrentHashMap<>();

  /** Scope's extras */
  private @NotNull Map<String, @NotNull Object> extra = new ConcurrentHashMap<>();

  /** Scope's event processor list */
  private @NotNull List<EventProcessorAndOrder> eventProcessors = new CopyOnWriteArrayList<>();

  /** Scope's SentryOptions */
  private volatile @NotNull SentryOptions options;

  // TODO Consider: Scope clone doesn't clone sessions

  /** Scope's current session */
  private volatile @Nullable Session session;

  /** Session lock, Ops should be atomic */
  private final @NotNull AutoClosableReentrantLock sessionLock = new AutoClosableReentrantLock();

  /** Transaction lock, Ops should be atomic */
  private final @NotNull AutoClosableReentrantLock transactionLock =
      new AutoClosableReentrantLock();

  /** PropagationContext lock, Ops should be atomic */
  private final @NotNull AutoClosableReentrantLock propagationContextLock =
      new AutoClosableReentrantLock();

  /** Scope's contexts */
  private @NotNull Contexts contexts = new Contexts();

  /** Scope's attachments */
  private @NotNull List<Attachment> attachments = new CopyOnWriteArrayList<>();

  private @NotNull PropagationContext propagationContext;

  /** Scope's session replay id */
  private @NotNull SentryId replayId = SentryId.EMPTY_ID;

  private @NotNull ISentryClient client = NoOpSentryClient.getInstance();

  private final @NotNull Map<Throwable, Pair<WeakReference<ISpan>, String>> throwableToSpan =
      Collections.synchronizedMap(new WeakHashMap<>());

  private final @NotNull IFeatureFlagBuffer featureFlags;

  /**
   * Scope's ctor
   *
   * @param options the options
   */
  public Scope(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required.");
    this.breadcrumbs = createBreadcrumbsList(this.options.getMaxBreadcrumbs());
    this.featureFlags = FeatureFlagBuffer.create(options);
    this.propagationContext = new PropagationContext();
    this.lastEventId = SentryId.EMPTY_ID;
  }

  private Scope(final @NotNull Scope scope) {
    this.transaction = scope.transaction;
    this.transactionName = scope.transactionName;
    this.session = scope.session;
    this.options = scope.options;
    this.level = scope.level;
    this.client = scope.client;
    this.lastEventId = scope.getLastEventId();

    final User userRef = scope.user;
    this.user = userRef != null ? new User(userRef) : null;
    this.screen = scope.screen;
    this.replayId = scope.replayId;

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

    this.featureFlags = scope.featureFlags.clone();

    this.propagationContext = new PropagationContext(scope.propagationContext);
  }

  /**
   * Returns the Scope's SentryLevel
   *
   * @return the SentryLevel
   */
  @Override
  public @Nullable SentryLevel getLevel() {
    return level;
  }

  /**
   * Sets the Scope's SentryLevel Level from scope exceptionally take precedence over the event
   *
   * @param level the SentryLevel
   */
  @Override
  public void setLevel(final @Nullable SentryLevel level) {
    this.level = level;

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setLevel(level);
    }
  }

  /**
   * Returns the Scope's transaction name.
   *
   * @return the transaction
   */
  @Override
  public @Nullable String getTransactionName() {
    final ITransaction tx = this.transaction;
    return tx != null ? tx.getName() : transactionName;
  }

  /**
   * Sets the Scope's transaction.
   *
   * @param transaction the transaction
   */
  @Override
  public void setTransaction(final @NotNull String transaction) {
    if (transaction != null) {
      final ITransaction tx = this.transaction;
      if (tx != null) {
        tx.setName(transaction, TransactionNameSource.CUSTOM);
      }
      this.transactionName = transaction;

      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setTransaction(transaction);
      }
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
  @Override
  public ISpan getSpan() {
    final @Nullable ISpan activeSpan = this.activeSpan.get();
    if (activeSpan != null) {
      return activeSpan;
    }

    final ITransaction tx = transaction;
    if (tx != null) {
      final ISpan span = tx.getLatestActiveSpan();

      if (span != null) {
        return span;
      }
    }
    return tx;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    activeSpan = new WeakReference<>(span);
  }

  /**
   * Sets the current active transaction
   *
   * @param transaction the transaction
   */
  @Override
  public void setTransaction(final @Nullable ITransaction transaction) {
    try (final @NotNull ISentryLifecycleToken ignored = transactionLock.acquire()) {
      this.transaction = transaction;

      for (final IScopeObserver observer : options.getScopeObservers()) {
        if (transaction != null) {
          observer.setTransaction(transaction.getName());
          observer.setTrace(transaction.getSpanContext(), this);
        } else {
          observer.setTransaction(null);
          observer.setTrace(null, this);
        }
      }
    }
  }

  /**
   * Returns the Scope's user
   *
   * @return the user
   */
  @Override
  public @Nullable User getUser() {
    return user;
  }

  /**
   * Sets the Scope's user
   *
   * @param user the user
   */
  @Override
  public void setUser(final @Nullable User user) {
    this.user = user;

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setUser(user);
    }
  }

  /**
   * Returns the Scope's current screen, previously set by {@link IScope#setScreen(String)}
   *
   * @return the name of the screen
   */
  @ApiStatus.Internal
  @Override
  public @Nullable String getScreen() {
    return screen;
  }

  /**
   * Sets the Scope's current screen
   *
   * @param screen the name of the screen
   */
  @ApiStatus.Internal
  @Override
  public void setScreen(final @Nullable String screen) {
    this.screen = screen;

    final @NotNull Contexts contexts = getContexts();
    @Nullable App app = contexts.getApp();
    if (app == null) {
      app = new App();
      contexts.setApp(app);
    }

    if (screen == null) {
      app.setViewNames(null);
    } else {
      final @NotNull List<String> viewNames = new ArrayList<>(1);
      viewNames.add(screen);
      app.setViewNames(viewNames);
    }

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setContexts(contexts);
    }
  }

  @Override
  public @NotNull SentryId getReplayId() {
    return replayId;
  }

  @Override
  public void setReplayId(final @NotNull SentryId replayId) {
    this.replayId = replayId;

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setReplayId(replayId);
    }
  }

  /**
   * Returns the Scope's request
   *
   * @return the request
   */
  @Override
  public @Nullable Request getRequest() {
    return request;
  }

  /**
   * Sets the Scope's request
   *
   * @param request the request
   */
  @Override
  public void setRequest(final @Nullable Request request) {
    this.request = request;

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setRequest(request);
    }
  }

  /**
   * Returns the Scope's fingerprint list
   *
   * @return the fingerprint list
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public List<String> getFingerprint() {
    return fingerprint;
  }

  /**
   * Sets the Scope's fingerprint list
   *
   * @param fingerprint the fingerprint list
   */
  @Override
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (fingerprint == null) {
      return;
    }
    this.fingerprint = new ArrayList<>(fingerprint);

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setFingerprint(fingerprint);
    }
  }

  /**
   * Returns the Scope's breadcrumbs queue
   *
   * @return the breadcrumbs queue
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public Queue<Breadcrumb> getBreadcrumbs() {
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
   * Adds a breadcrumb to the breadcrumbs queue. It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   * @param hint the hint
   */
  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    if (breadcrumb == null || breadcrumbs instanceof DisabledQueue) {
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

      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.addBreadcrumb(breadcrumb);
        observer.setBreadcrumbs(breadcrumbs);
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
  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  /** Clear all the breadcrumbs */
  @Override
  public void clearBreadcrumbs() {
    breadcrumbs.clear();

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setBreadcrumbs(breadcrumbs);
    }
  }

  /** Clears the transaction. */
  @Override
  public void clearTransaction() {
    try (final @NotNull ISentryLifecycleToken ignored = transactionLock.acquire()) {
      transaction = null;
    }
    transactionName = null;

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setTransaction(null);
      observer.setTrace(null, this);
    }
  }

  /**
   * Returns active transaction or null if there is no active transaction.
   *
   * @return the transaction
   */
  @Nullable
  @Override
  public ITransaction getTransaction() {
    return this.transaction;
  }

  /** Resets the Scope to its default state */
  @Override
  public void clear() {
    level = null;
    user = null;
    request = null;
    screen = null;
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
  @Override
  public @NotNull Map<String, String> getTags() {
    return CollectionUtils.newConcurrentHashMap(tags);
  }

  /**
   * Sets a tag to Scope's tags
   *
   * @param key the key
   * @param value the value
   */
  @Override
  public void setTag(final @Nullable String key, final @Nullable String value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      removeTag(key);
    } else {
      this.tags.put(key, value);

      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setTag(key, value);
        observer.setTags(tags);
      }
    }
  }

  /**
   * Removes a tag from the Scope's tags
   *
   * @param key the key
   */
  @Override
  public void removeTag(final @Nullable String key) {
    if (key == null) {
      return;
    }
    this.tags.remove(key);

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.removeTag(key);
      observer.setTags(tags);
    }
  }

  /**
   * Returns the Scope's extra map
   *
   * @return the extra map
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public Map<String, Object> getExtras() {
    return extra;
  }

  /**
   * Sets an extra to the Scope's extra map
   *
   * @param key the key
   * @param value the value
   */
  @Override
  public void setExtra(final @Nullable String key, final @Nullable String value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      removeExtra(key);
    } else {
      this.extra.put(key, value);

      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setExtra(key, value);
        observer.setExtras(extra);
      }
    }
  }

  /**
   * Removes an extra from the Scope's extras
   *
   * @param key the key
   */
  @Override
  public void removeExtra(final @Nullable String key) {
    if (key == null) {
      return;
    }
    this.extra.remove(key);

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.removeExtra(key);
      observer.setExtras(extra);
    }
  }

  /**
   * Returns the Scope's contexts
   *
   * @return the contexts
   */
  @Override
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Object value) {
    if (key == null) {
      return;
    }
    this.contexts.put(key, value);

    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setContexts(contexts);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Boolean value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, Boolean> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable String value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, String> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Number value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, Number> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Collection<?> value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, Collection<?>> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Object[] value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, Object[]> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  @Override
  public void setContexts(final @Nullable String key, final @Nullable Character value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      // unset
      setContexts(key, (Object) null);
    } else {
      final Map<String, Character> map = new HashMap<>();
      map.put("value", value);
      setContexts(key, map);
    }
  }

  /**
   * Removes a value from the Scope's contexts
   *
   * @param key the Key
   */
  @Override
  public void removeContexts(final @Nullable String key) {
    if (key == null) {
      return;
    }
    contexts.remove(key);
  }

  /**
   * Returns the Scopes's attachments
   *
   * @return the attachments
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public List<Attachment> getAttachments() {
    return new CopyOnWriteArrayList<>(attachments);
  }

  /**
   * Adds an attachment to the Scope's list of attachments. The SDK adds the attachment to every
   * event and transaction sent to Sentry.
   *
   * @param attachment The attachment to add to the Scope's list of attachments.
   */
  @Override
  public void addAttachment(final @NotNull Attachment attachment) {
    attachments.add(attachment);
  }

  /** Clear all attachments. */
  @Override
  public void clearAttachments() {
    attachments.clear();
  }

  /**
   * Creates a breadcrumb list with the max number of breadcrumbs
   *
   * @param maxBreadcrumb the max number of breadcrumbs
   * @return the breadcrumbs queue
   */
  static @NotNull Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return maxBreadcrumb > 0
        ? SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb))
        : new DisabledQueue<>();
  }

  /**
   * Returns the Scope's event processors
   *
   * @return the event processors list
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public List<EventProcessor> getEventProcessors() {
    return EventProcessorUtils.unwrap(eventProcessors);
  }

  /**
   * Returns the Scope's event processors including their order
   *
   * @return the event processors list and their order
   */
  @ApiStatus.Internal
  @NotNull
  @Override
  public List<EventProcessorAndOrder> getEventProcessorsWithOrder() {
    return eventProcessors;
  }

  /**
   * Adds an event processor to the Scope's event processors list
   *
   * @param eventProcessor the event processor
   */
  @Override
  public void addEventProcessor(final @NotNull EventProcessor eventProcessor) {
    eventProcessors.add(new EventProcessorAndOrder(eventProcessor, eventProcessor.getOrder()));
  }

  /**
   * Callback to do atomic operations on session
   *
   * @param sessionCallback the IWithSession callback
   * @return a clone of the Session after executing the callback and mutating the session
   */
  @ApiStatus.Internal
  @Nullable
  @Override
  public Session withSession(final @NotNull IWithSession sessionCallback) {
    Session cloneSession = null;
    try (final @NotNull ISentryLifecycleToken ignored = sessionLock.acquire()) {
      sessionCallback.accept(session);

      if (session != null) {
        cloneSession = session.clone();
      }
    }
    return cloneSession;
  }

  /** The IWithSession callback */
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
  @ApiStatus.Internal
  @Nullable
  @Override
  public SessionPair startSession() {
    Session previousSession;
    SessionPair pair = null;
    try (final @NotNull ISentryLifecycleToken ignored = sessionLock.acquire()) {
      if (session != null) {
        // Assumes session will NOT flush itself (Not passing any scopes to it)
        session.end();
        // Continuous profiler sample rate is reevaluated every time a session ends
        options.getContinuousProfiler().reevaluateSampling();
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

    /** The previous session if exists */
    private final @Nullable Session previous;

    /** The current Session */
    private final @NotNull Session current;

    /**
     * The SessionPair ctor
     *
     * @param current the current session
     * @param previous the previous sessions if exists or null
     */
    public SessionPair(final @NotNull Session current, final @Nullable Session previous) {
      this.current = current;
      this.previous = previous;
    }

    /**
     * Returns the previous session
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
  @ApiStatus.Internal
  @Nullable
  @Override
  public Session endSession() {
    Session previousSession = null;
    try (final @NotNull ISentryLifecycleToken ignored = sessionLock.acquire()) {
      if (session != null) {
        session.end();
        // Continuous profiler sample rate is reevaluated every time a session ends
        options.getContinuousProfiler().reevaluateSampling();
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
  @Override
  public void withTransaction(final @NotNull IWithTransaction callback) {
    try (final @NotNull ISentryLifecycleToken ignored = transactionLock.acquire()) {
      callback.accept(transaction);
    }
  }

  @ApiStatus.Internal
  @NotNull
  @Override
  public SentryOptions getOptions() {
    return options;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Session getSession() {
    return session;
  }

  @ApiStatus.Internal
  @Override
  public void clearSession() {
    session = null;
  }

  @ApiStatus.Internal
  @Override
  public void setPropagationContext(final @NotNull PropagationContext propagationContext) {
    this.propagationContext = propagationContext;

    final @NotNull SpanContext spanContext = propagationContext.toSpanContext();
    for (final IScopeObserver observer : options.getScopeObservers()) {
      observer.setTrace(spanContext, this);
    }
  }

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext getPropagationContext() {
    return propagationContext;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext withPropagationContext(
      final @NotNull IWithPropagationContext callback) {
    try (final @NotNull ISentryLifecycleToken ignored = propagationContextLock.acquire()) {
      callback.accept(propagationContext);
      return new PropagationContext(propagationContext);
    }
  }

  /**
   * Clones the Scope
   *
   * @return the cloned Scope
   */
  @Override
  public @NotNull IScope clone() {
    return new Scope(this);
  }

  @Override
  public void setLastEventId(@NotNull SentryId lastEventId) {
    this.lastEventId = lastEventId;
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return lastEventId;
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {
    this.client = client;
  }

  @Override
  public @NotNull ISentryClient getClient() {
    return client;
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    featureFlags.add(flag, result);
  }

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    return featureFlags.getFeatureFlags();
  }

  @Override
  public @NotNull IFeatureFlagBuffer getFeatureFlagBuffer() {
    return featureFlags;
  }

  @Override
  @ApiStatus.Internal
  public void assignTraceContext(final @NotNull SentryEvent event) {
    if (options.isTracingEnabled() && event.getThrowable() != null) {
      final Pair<WeakReference<ISpan>, String> pair =
          throwableToSpan.get(ExceptionUtils.findRootCause(event.getThrowable()));
      if (pair != null) {
        final WeakReference<ISpan> spanWeakRef = pair.getFirst();
        if (event.getContexts().getTrace() == null && spanWeakRef != null) {
          final ISpan span = spanWeakRef.get();
          if (span != null) {
            event.getContexts().setTrace(span.getSpanContext());
          }
        }
        final String transactionName = pair.getSecond();
        if (event.getTransaction() == null && transactionName != null) {
          event.setTransaction(transactionName);
        }
      }
    }
  }

  @Override
  @ApiStatus.Internal
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    Objects.requireNonNull(throwable, "throwable is required");
    Objects.requireNonNull(span, "span is required");
    Objects.requireNonNull(transactionName, "transactionName is required");
    // to match any cause, span context is always attached to the root cause of the exception
    final Throwable rootCause = ExceptionUtils.findRootCause(throwable);
    // the most inner span should be assigned to a throwable
    if (!throwableToSpan.containsKey(rootCause)) {
      throwableToSpan.put(rootCause, new Pair<>(new WeakReference<>(span), transactionName));
    }
  }

  @ApiStatus.Internal
  @Override
  public void replaceOptions(final @NotNull SentryOptions options) {
    this.options = options;
    final Queue<Breadcrumb> oldBreadcrumbs = breadcrumbs;
    breadcrumbs = createBreadcrumbsList(options.getMaxBreadcrumbs());
    for (Breadcrumb breadcrumb : oldBreadcrumbs) {
      /*
      this should trigger beforeBreadcrumb
      and notify observers for breadcrumbs added before options where customized in Sentry.init
      */
      addBreadcrumb(breadcrumb);
    }
  }

  /** The IWithTransaction callback */
  @ApiStatus.Internal
  public interface IWithTransaction {

    /**
     * The accept method of the callback
     *
     * @param transaction the current transaction or null if none exists
     */
    void accept(@Nullable ITransaction transaction);
  }

  /** the IWithPropagationContext callback */
  @ApiStatus.Internal
  public interface IWithPropagationContext {

    /**
     * The accept method of the callback
     *
     * @param propagationContext the current propagationContext
     */
    void accept(@NotNull PropagationContext propagationContext);
  }
}
