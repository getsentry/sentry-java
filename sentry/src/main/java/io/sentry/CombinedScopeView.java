package io.sentry;

import static io.sentry.Scope.createBreadcrumbsList;

import io.sentry.featureflags.FeatureFlagBuffer;
import io.sentry.featureflags.IFeatureFlagBuffer;
import io.sentry.internal.eventprocessor.EventProcessorAndOrder;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.FeatureFlags;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.EventProcessorUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
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
  public void setActiveSpan(final @Nullable ISpan span) {
    scope.setActiveSpan(span);
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
    final @NotNull Queue<Breadcrumb> globalBreadcrumbs = globalScope.getBreadcrumbs();
    final @NotNull Queue<Breadcrumb> isolationBreadcrumbs = isolationScope.getBreadcrumbs();
    final @NotNull Queue<Breadcrumb> currentBreadcrumbs = scope.getBreadcrumbs();

    final boolean hasGlobalBreadcrumbs = !globalBreadcrumbs.isEmpty();
    final boolean hasIsolationBreadcrumbs = !isolationBreadcrumbs.isEmpty();
    final boolean hasCurrentBreadcrumbs = !currentBreadcrumbs.isEmpty();

    if (!hasGlobalBreadcrumbs && !hasIsolationBreadcrumbs && !hasCurrentBreadcrumbs) {
      return getDefaultScopeValue(globalBreadcrumbs, isolationBreadcrumbs, currentBreadcrumbs);
    }
    if (!hasIsolationBreadcrumbs && !hasCurrentBreadcrumbs) {
      return globalBreadcrumbs;
    }
    if (!hasGlobalBreadcrumbs && !hasCurrentBreadcrumbs) {
      return isolationBreadcrumbs;
    }
    if (!hasGlobalBreadcrumbs && !hasIsolationBreadcrumbs) {
      return currentBreadcrumbs;
    }

    final @NotNull List<Breadcrumb> allBreadcrumbs = new ArrayList<>();
    allBreadcrumbs.addAll(globalBreadcrumbs);
    allBreadcrumbs.addAll(isolationBreadcrumbs);
    allBreadcrumbs.addAll(currentBreadcrumbs);
    Collections.sort(allBreadcrumbs);

    final @NotNull Queue<Breadcrumb> breadcrumbs =
        createBreadcrumbsList(scope.getOptions().getMaxBreadcrumbs());
    breadcrumbs.addAll(allBreadcrumbs);

    return breadcrumbs;
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
    final @NotNull Map<String, String> globalTags = globalScope.getTags();
    final @NotNull Map<String, String> isolationTags = isolationScope.getTags();
    final @NotNull Map<String, String> currentTags = scope.getTags();

    final boolean hasGlobalTags = !globalTags.isEmpty();
    final boolean hasIsolationTags = !isolationTags.isEmpty();
    final boolean hasCurrentTags = !currentTags.isEmpty();

    if (!hasGlobalTags && !hasIsolationTags && !hasCurrentTags) {
      return getDefaultScopeValue(globalTags, isolationTags, currentTags);
    }
    if (!hasIsolationTags && !hasCurrentTags) {
      return globalTags;
    }
    if (!hasGlobalTags && !hasCurrentTags) {
      return isolationTags;
    }
    if (!hasGlobalTags && !hasIsolationTags) {
      return currentTags;
    }

    final @NotNull Map<String, String> allTags = new ConcurrentHashMap<>();
    allTags.putAll(globalTags);
    allTags.putAll(isolationTags);
    allTags.putAll(currentTags);
    return allTags;
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    getDefaultWriteScope().setTag(key, value);
  }

  @Override
  public void removeTag(@Nullable String key) {
    getDefaultWriteScope().removeTag(key);
  }

  @Override
  public @NotNull Map<String, SentryAttribute> getAttributes() {
    final @NotNull Map<String, SentryAttribute> globalAttributes = globalScope.getAttributes();
    final @NotNull Map<String, SentryAttribute> isolationAttributes =
        isolationScope.getAttributes();
    final @NotNull Map<String, SentryAttribute> currentAttributes = scope.getAttributes();

    final boolean hasGlobalAttributes = !globalAttributes.isEmpty();
    final boolean hasIsolationAttributes = !isolationAttributes.isEmpty();
    final boolean hasCurrentAttributes = !currentAttributes.isEmpty();

    if (!hasGlobalAttributes && !hasIsolationAttributes && !hasCurrentAttributes) {
      return getDefaultScopeValue(globalAttributes, isolationAttributes, currentAttributes);
    }
    if (!hasIsolationAttributes && !hasCurrentAttributes) {
      return globalAttributes;
    }
    if (!hasGlobalAttributes && !hasCurrentAttributes) {
      return isolationAttributes;
    }
    if (!hasGlobalAttributes && !hasIsolationAttributes) {
      return currentAttributes;
    }

    final @NotNull Map<String, SentryAttribute> allAttributes = new ConcurrentHashMap<>();
    allAttributes.putAll(globalAttributes);
    allAttributes.putAll(isolationAttributes);
    allAttributes.putAll(currentAttributes);
    return allAttributes;
  }

  @Override
  public void setAttribute(@Nullable String key, @Nullable Object value) {
    getDefaultWriteScope().setAttribute(key, value);
  }

  @Override
  public void setAttribute(@Nullable SentryAttribute attribute) {
    getDefaultWriteScope().setAttribute(attribute);
  }

  @Override
  public void setAttributes(@Nullable SentryAttributes attributes) {
    getDefaultWriteScope().setAttributes(attributes);
  }

  @Override
  public void removeAttribute(@Nullable String key) {
    getDefaultWriteScope().removeAttribute(key);
  }

  @Override
  public @NotNull Map<String, Object> getExtras() {
    final @NotNull Map<String, Object> globalExtras = globalScope.getExtras();
    final @NotNull Map<String, Object> isolationExtras = isolationScope.getExtras();
    final @NotNull Map<String, Object> currentExtras = scope.getExtras();

    final boolean hasGlobalExtras = !globalExtras.isEmpty();
    final boolean hasIsolationExtras = !isolationExtras.isEmpty();
    final boolean hasCurrentExtras = !currentExtras.isEmpty();

    if (!hasGlobalExtras && !hasIsolationExtras && !hasCurrentExtras) {
      return getDefaultScopeValue(globalExtras, isolationExtras, currentExtras);
    }
    if (!hasIsolationExtras && !hasCurrentExtras) {
      return globalExtras;
    }
    if (!hasGlobalExtras && !hasCurrentExtras) {
      return isolationExtras;
    }
    if (!hasGlobalExtras && !hasIsolationExtras) {
      return currentExtras;
    }

    final @NotNull Map<String, Object> allExtras = new ConcurrentHashMap<>();
    allExtras.putAll(globalExtras);
    allExtras.putAll(isolationExtras);
    allExtras.putAll(currentExtras);
    return allExtras;
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {
    getDefaultWriteScope().setExtra(key, value);
  }

  @Override
  public void removeExtra(@Nullable String key) {
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
  public void setContexts(@Nullable String key, @Nullable Object value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Boolean value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable String value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Number value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Collection<?> value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Object[] value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Character value) {
    getDefaultWriteScope().setContexts(key, value);
  }

  @Override
  public void removeContexts(@Nullable String key) {
    getDefaultWriteScope().removeContexts(key);
  }

  private @NotNull IScope getDefaultWriteScope() {
    return getSpecificScope(null);
  }

  private <T> @NotNull T getDefaultScopeValue(
      final @NotNull T globalValue,
      final @NotNull T isolationValue,
      final @NotNull T currentValue) {
    switch (getOptions().getDefaultScopeType()) {
      case CURRENT:
        return currentValue;
      case ISOLATION:
        return isolationValue;
      case GLOBAL:
        return globalValue;
      default:
        // calm the compiler
        return currentValue;
    }
  }

  IScope getSpecificScope(final @Nullable ScopeType scopeType) {
    if (scopeType != null) {
      switch (scopeType) {
        case CURRENT:
          return scope;
        case ISOLATION:
          return isolationScope;
        case GLOBAL:
          return globalScope;
        case COMBINED:
          return this;
        default:
          break;
      }
    }

    switch (getOptions().getDefaultScopeType()) {
      case CURRENT:
        return scope;
      case ISOLATION:
        return isolationScope;
      case GLOBAL:
        return globalScope;
      default:
        // calm the compiler
        return scope;
    }
  }

  @Override
  public @NotNull List<Attachment> getAttachments() {
    final @NotNull List<Attachment> globalAttachments = globalScope.getAttachments();
    final @NotNull List<Attachment> isolationAttachments = isolationScope.getAttachments();
    final @NotNull List<Attachment> currentAttachments = scope.getAttachments();

    final boolean hasGlobalAttachments = !globalAttachments.isEmpty();
    final boolean hasIsolationAttachments = !isolationAttachments.isEmpty();
    final boolean hasCurrentAttachments = !currentAttachments.isEmpty();

    if (!hasGlobalAttachments && !hasIsolationAttachments && !hasCurrentAttachments) {
      return getDefaultScopeValue(globalAttachments, isolationAttachments, currentAttachments);
    }
    if (!hasIsolationAttachments && !hasCurrentAttachments) {
      return globalAttachments;
    }
    if (!hasGlobalAttachments && !hasCurrentAttachments) {
      return isolationAttachments;
    }
    if (!hasGlobalAttachments && !hasIsolationAttachments) {
      return currentAttachments;
    }

    final @NotNull List<Attachment> allAttachments = new CopyOnWriteArrayList<>();
    allAttachments.addAll(globalAttachments);
    allAttachments.addAll(isolationAttachments);
    allAttachments.addAll(currentAttachments);
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
  public @NotNull List<EventProcessorAndOrder> getEventProcessorsWithOrder() {
    final @NotNull List<EventProcessorAndOrder> allEventProcessors = new CopyOnWriteArrayList<>();
    allEventProcessors.addAll(globalScope.getEventProcessorsWithOrder());
    allEventProcessors.addAll(isolationScope.getEventProcessorsWithOrder());
    allEventProcessors.addAll(scope.getEventProcessorsWithOrder());
    Collections.sort(allEventProcessors);
    return allEventProcessors;
  }

  @Override
  public @NotNull List<EventProcessor> getEventProcessors() {
    return EventProcessorUtils.unwrap(getEventProcessorsWithOrder());
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
    return globalScope.getOptions();
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
  public void clearSession() {
    getDefaultWriteScope().clearSession();
  }

  @Override
  public void setPropagationContext(@NotNull PropagationContext propagationContext) {
    getDefaultWriteScope().setPropagationContext(propagationContext);
  }

  @ApiStatus.Internal
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
    return new CombinedScopeView(globalScope, isolationScope.clone(), scope.clone());
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

  @ApiStatus.Internal
  @Override
  public void replaceOptions(@NotNull SentryOptions options) {
    globalScope.replaceOptions(options);
  }

  @Override
  public @NotNull SentryId getReplayId() {
    final @NotNull SentryId current = scope.getReplayId();
    if (!SentryId.EMPTY_ID.equals(current)) {
      return current;
    }
    final @Nullable SentryId isolation = isolationScope.getReplayId();
    if (!SentryId.EMPTY_ID.equals(isolation)) {
      return isolation;
    }
    return globalScope.getReplayId();
  }

  @Override
  public void setReplayId(@NotNull SentryId replayId) {
    getDefaultWriteScope().setReplayId(replayId);
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    getDefaultWriteScope().addFeatureFlag(flag, result);
    final @Nullable ISpan span = getSpan();
    if (span != null) {
      span.addFeatureFlag(flag, result);
    }
  }

  @Override
  public void clearFeatureFlags() {
    getDefaultWriteScope().clearFeatureFlags();
  }

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    return getFeatureFlagBuffer().getFeatureFlags();
  }

  @Override
  public @NotNull IFeatureFlagBuffer getFeatureFlagBuffer() {
    return FeatureFlagBuffer.merged(
        getOptions(),
        globalScope.getFeatureFlagBuffer(),
        isolationScope.getFeatureFlagBuffer(),
        scope.getFeatureFlagBuffer());
  }
}
