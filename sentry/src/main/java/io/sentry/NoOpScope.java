package io.sentry;

import io.sentry.featureflags.IFeatureFlagBuffer;
import io.sentry.featureflags.NoOpFeatureFlagBuffer;
import io.sentry.internal.eventprocessor.EventProcessorAndOrder;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.FeatureFlags;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.LazyEvaluator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpScope implements IScope {

  private static final NoOpScope instance = new NoOpScope();

  private final @NotNull LazyEvaluator<SentryOptions> emptyOptions =
      new LazyEvaluator<>(() -> SentryOptions.empty());

  private NoOpScope() {}

  public static NoOpScope getInstance() {
    return instance;
  }

  @Override
  public @Nullable SentryLevel getLevel() {
    return null;
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {}

  @Override
  public @Nullable String getTransactionName() {
    return null;
  }

  @Override
  public void setTransaction(@NotNull String transaction) {}

  @Override
  public @Nullable ISpan getSpan() {
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {}

  @Override
  public void setTransaction(@Nullable ITransaction transaction) {}

  @Override
  public @Nullable User getUser() {
    return null;
  }

  @Override
  public void setUser(@Nullable User user) {}

  @ApiStatus.Internal
  @Override
  public @Nullable String getScreen() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void setScreen(@Nullable String screen) {}

  @Override
  public @NotNull SentryId getReplayId() {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void setReplayId(@Nullable SentryId replayId) {}

  @Override
  public @Nullable Request getRequest() {
    return null;
  }

  @Override
  public void setRequest(@Nullable Request request) {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<String> getFingerprint() {
    return new ArrayList<>();
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Queue<Breadcrumb> getBreadcrumbs() {
    return new ArrayDeque<>();
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {}

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {}

  @Override
  public void clearBreadcrumbs() {}

  @Override
  public void clearTransaction() {}

  @Override
  public @Nullable ITransaction getTransaction() {
    return null;
  }

  @Override
  public void clear() {}

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, String> getTags() {
    return new HashMap<>();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeTag(@Nullable String key) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, Object> getExtras() {
    return new HashMap<>();
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeExtra(@Nullable String key) {}

  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Object value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Boolean value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable String value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Number value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Collection<?> value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Object[] value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Character value) {}

  @Override
  public void removeContexts(@Nullable String key) {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<Attachment> getAttachments() {
    return new ArrayList<>();
  }

  @Override
  public void addAttachment(@NotNull Attachment attachment) {}

  @Override
  public void clearAttachments() {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<EventProcessor> getEventProcessors() {
    return new ArrayList<>();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull List<EventProcessorAndOrder> getEventProcessorsWithOrder() {
    return new ArrayList<>();
  }

  @Override
  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {}

  @ApiStatus.Internal
  @Override
  public @Nullable Session withSession(Scope.@NotNull IWithSession sessionCallback) {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Scope.SessionPair startSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Session endSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void withTransaction(Scope.@NotNull IWithTransaction callback) {}

  @ApiStatus.Internal
  @Override
  public @NotNull SentryOptions getOptions() {
    return emptyOptions.getValue();
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Session getSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void clearSession() {}

  @ApiStatus.Internal
  @Override
  public void setPropagationContext(@NotNull PropagationContext propagationContext) {}

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext getPropagationContext() {
    return new PropagationContext();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext withPropagationContext(
      Scope.@NotNull IWithPropagationContext callback) {
    return new PropagationContext();
  }

  @Override
  public void setLastEventId(@NotNull SentryId lastEventId) {}

  /**
   * Clones the Scope
   *
   * @return the cloned Scope
   */
  @Override
  public @NotNull IScope clone() {
    return NoOpScope.getInstance();
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {}

  @Override
  public @NotNull ISentryClient getClient() {
    return NoOpSentryClient.getInstance();
  }

  @Override
  public void assignTraceContext(@NotNull SentryEvent event) {}

  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {}

  @Override
  public void replaceOptions(@NotNull SentryOptions options) {}

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    return null;
  }

  @Override
  public @NotNull IFeatureFlagBuffer getFeatureFlagBuffer() {
    return NoOpFeatureFlagBuffer.getInstance();
  }
}
