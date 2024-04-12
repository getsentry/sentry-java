package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CombinedScopeView implements IScope {

  private final IScope globalScope;
  private final IScope isolationScope;
  private final IScope scope;

  public CombinedScopeView(
      final @NotNull IScope globalScope,
      final @NotNull IScope isolationScope,
      final @NotNull IScope scope) {
    this.globalScope = globalScope;
    this.isolationScope = isolationScope;
    this.scope = scope;
  }

  @Override
  public @Nullable SentryLevel getLevel() {
    final @Nullable SentryLevel current = scope.getLevel();
    if (current != null) {
      return current;
    }
    final @Nullable SentryLevel isolation = isolationScope.getLevel();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getLevel();
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    getDefaultWriteScope().setLevel(level);
  }

  @Override
  public @Nullable String getTransactionName() {
    final @Nullable String current = scope.getTransactionName();
    if (current != null) {
      return current;
    }
    final @Nullable String isolation = isolationScope.getTransactionName();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getTransactionName();
  }

  @Override
  public void setTransaction(@NotNull String transaction) {
    getDefaultWriteScope().setTransaction(transaction);
  }

  @Override
  public @Nullable ISpan getSpan() {
    final @Nullable ISpan current = scope.getSpan();
    if (current != null) {
      return current;
    }
    final @Nullable ISpan isolation = isolationScope.getSpan();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getSpan();
  }

  @Override
  public void setTransaction(@Nullable ITransaction transaction) {
    getDefaultWriteScope().setTransaction(transaction);
  }

  @Override
  public @Nullable User getUser() {
    final @Nullable User current = scope.getUser();
    if (current != null) {
      return current;
    }
    final @Nullable User isolation = isolationScope.getUser();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getUser();
  }

  @Override
  public void setUser(@Nullable User user) {
    getDefaultWriteScope().setUser(user);
  }

  @Override
  public @Nullable String getScreen() {
    final @Nullable String current = scope.getScreen();
    if (current != null) {
      return current;
    }
    final @Nullable String isolation = isolationScope.getScreen();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getScreen();
  }

  @Override
  public void setScreen(@Nullable String screen) {
    getDefaultWriteScope().setScreen(screen);
  }

  @Override
  public @Nullable Request getRequest() {
    final @Nullable Request current = scope.getRequest();
    if (current != null) {
      return current;
    }
    final @Nullable Request isolation = isolationScope.getRequest();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getRequest();
  }

  @Override
  public void setRequest(@Nullable Request request) {
    getDefaultWriteScope().setRequest(request);
  }

  @Override
  public @NotNull List<String> getFingerprint() {
    final @Nullable List<String> current = scope.getFingerprint();
    if (!current.isEmpty()) {
      return current;
    }
    final @Nullable List<String> isolation = isolationScope.getFingerprint();
    if (!isolation.isEmpty()) {
      return isolation;
    }
    return globalScope.getFingerprint();
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    getDefaultWriteScope().setFingerprint(fingerprint);
  }

  @Override
  public @NotNull Queue<Breadcrumb> getBreadcrumbs() {
    final @NotNull List<Breadcrumb> allBreadcrumbs = new ArrayList<>();
    allBreadcrumbs.addAll(globalScope.getBreadcrumbs());
    allBreadcrumbs.addAll(isolationScope.getBreadcrumbs());
    allBreadcrumbs.addAll(scope.getBreadcrumbs());
    Collections.sort(allBreadcrumbs);

    // TODO test oldest are removed first
    final @NotNull Queue<Breadcrumb> breadcrumbs =
        createBreadcrumbsList(scope.getOptions().getMaxBreadcrumbs());
    breadcrumbs.addAll(allBreadcrumbs);

    return breadcrumbs;
  }

  /**
   * Creates a breadcrumb list with the max number of breadcrumbs
   *
   * @param maxBreadcrumb the max number of breadcrumbs
   * @return the breadcrumbs queue
   */
  // TODO copied from Scope, should reuse instead
  private @NotNull Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb));
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    getDefaultWriteScope().addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    getDefaultWriteScope().addBreadcrumb(breadcrumb);
  }

  @Override
  public void clearBreadcrumbs() {
    getDefaultWriteScope().clearBreadcrumbs();
  }

  @Override
  public void clearTransaction() {
    getDefaultWriteScope().clearTransaction();
  }

  @Override
  public @Nullable ITransaction getTransaction() {
    final @Nullable ITransaction current = scope.getTransaction();
    if (current != null) {
      return current;
    }
    final @Nullable ITransaction isolation = isolationScope.getTransaction();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getTransaction();
  }

  @Override
  public void clear() {
    getDefaultWriteScope().clear();
  }

  @Override
  public @NotNull Map<String, String> getTags() {
    final @NotNull Map<String, String> allTags = new ConcurrentHashMap<>();
    allTags.putAll(globalScope.getTags());
    allTags.putAll(isolationScope.getTags());
    allTags.putAll(scope.getTags());
    return allTags;
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    getDefaultWriteScope().setTag(key, value);
  }

  @Override
  public void removeTag(@NotNull String key) {
    // TODO should this go to all scopes?
    getDefaultWriteScope().removeTag(key);
  }

  @Override
  public @NotNull Map<String, Object> getExtras() {
    final @NotNull Map<String, Object> allTags = new ConcurrentHashMap<>();
    allTags.putAll(globalScope.getExtras());
    allTags.putAll(isolationScope.getExtras());
    allTags.putAll(scope.getExtras());
    return allTags;
  }

  @Override
  public void setExtra(@NotNull String key, @NotNull String value) {
    getDefaultWriteScope().setExtra(key, value);
  }

  @Override
  public void removeExtra(@NotNull String key) {
    // TODO should this go to all scopes?
    getDefaultWriteScope().removeExtra(key);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return new CombinedContextsView(
        globalScope.getContexts(),
        isolationScope.getContexts(),
        scope.getContexts(),
        getOptions().getDefaultScopeType());
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Object value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Boolean value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull String value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Number value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Collection<?> value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Object[] value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@NotNull String key, @NotNull Character value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void removeContexts(@NotNull String key) {
    // TODO should this go to all scopes?
    getDefaultWriteScope().removeContexts(key);
  }

  private @NotNull IScope getDefaultWriteScope() {
    // TODO use Scopes.getSpecificScope?
    if (ScopeType.CURRENT.equals(getOptions().getDefaultScopeType())) {
      return scope;
    }
    if (ScopeType.ISOLATION.equals(getOptions().getDefaultScopeType())) {
      return isolationScope;
    }
    return globalScope;
  }

  @Override
  public @NotNull List<Attachment> getAttachments() {
    final @NotNull List<Attachment> allAttachments = new CopyOnWriteArrayList<>();
    allAttachments.addAll(globalScope.getAttachments());
    allAttachments.addAll(isolationScope.getAttachments());
    allAttachments.addAll(scope.getAttachments());
    return allAttachments;
  }

  @Override
  public void addAttachment(@NotNull Attachment attachment) {
    getDefaultWriteScope().addAttachment(attachment);
  }

  @Override
  public void clearAttachments() {
    getDefaultWriteScope().clearAttachments();
  }

  @Override
  public @NotNull List<EventProcessor> getEventProcessors() {
    // TODO mechanism for ordering event processors
    final @NotNull List<EventProcessor> allEventProcessors = new CopyOnWriteArrayList<>();
    allEventProcessors.addAll(globalScope.getEventProcessors());
    allEventProcessors.addAll(isolationScope.getEventProcessors());
    allEventProcessors.addAll(scope.getEventProcessors());
    return allEventProcessors;
  }

  @Override
  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {
    getDefaultWriteScope().addEventProcessor(eventProcessor);
  }

  @Override
  public @Nullable Session withSession(Scope.@NotNull IWithSession sessionCallback) {
    return getDefaultWriteScope().withSession(sessionCallback);
  }

  @Override
  public @Nullable Scope.SessionPair startSession() {
    return getDefaultWriteScope().startSession();
  }

  @Override
  public @Nullable Session endSession() {
    return getDefaultWriteScope().endSession();
  }

  @Override
  public void withTransaction(Scope.@NotNull IWithTransaction callback) {
    getDefaultWriteScope().withTransaction(callback);
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return scope.getOptions();
  }

  @Override
  public @Nullable Session getSession() {
    final @Nullable Session current = scope.getSession();
    if (current != null) {
      return current;
    }
    final @Nullable Session isolation = isolationScope.getSession();
    if (isolation != null) {
      return isolation;
    }
    return globalScope.getSession();
  }

  @Override
  public void setPropagationContext(@NotNull PropagationContext propagationContext) {
    getDefaultWriteScope().setPropagationContext(propagationContext);
  }

  @Override
  public @NotNull PropagationContext getPropagationContext() {
    return getDefaultWriteScope().getPropagationContext();
  }

  @Override
  public @NotNull PropagationContext withPropagationContext(
      Scope.@NotNull IWithPropagationContext callback) {
    return getDefaultWriteScope().withPropagationContext(callback);
  }

  @Override
  public @NotNull IScope clone() {
    // TODO just return a new CombinedScopeView with forked scope?
    return getDefaultWriteScope().clone();
  }

  @Override
  public void setLastEventId(@NotNull SentryId lastEventId) {
    globalScope.setLastEventId(lastEventId);
    isolationScope.setLastEventId(lastEventId);
    scope.setLastEventId(lastEventId);
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return globalScope.getLastEventId();
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {
    getDefaultWriteScope().bindClient(client);
  }

  @Override
  public @NotNull ISentryClient getClient() {
    // TODO checking for noop here doesn't allow disabling via client, is that ok?
    final @Nullable ISentryClient current = scope.getClient();
    if (!(current instanceof NoOpSentryClient)) {
      return current;
    }
    final @Nullable ISentryClient isolation = isolationScope.getClient();
    if (!(isolation instanceof NoOpSentryClient)) {
      return isolation;
    }
    return globalScope.getClient();
  }

  @Override
  public void assignTraceContext(@NotNull SentryEvent event) {
    globalScope.assignTraceContext(event);
  }

  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {
    globalScope.setSpanContext(throwable, span, transactionName);
  }
}
