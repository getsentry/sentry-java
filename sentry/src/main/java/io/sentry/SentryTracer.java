package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryTracer implements ISpan {
  private final @NotNull Span root;
  private final @NotNull List<Span> children = new CopyOnWriteArrayList<>();
  private final @NotNull IHub hub;

  public SentryTracer(final @NotNull TransactionContext context, final @NotNull IHub hub) {
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(hub, "hub is required");
    this.root = new Span(context, this, hub);
    this.root.setName(context.getName());
    this.hub = hub;
  }

  public @NotNull List<Span> getChildren() {
    return children;
  }

  public @NotNull Date getStartTimestamp() {
    return this.root.getStartTimestamp();
  }

  public @Nullable Date getTimestamp() {
    return this.root.getTimestamp();
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @param operation - span operation name
   * @param description - span description
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(
      final @NotNull SpanId parentSpanId,
      final @NotNull String operation,
      final @Nullable String description) {
    final ISpan span = startChild(parentSpanId, operation);
    span.setDescription(description);
    return span;
  }

  /**
   * Starts a child Span with given trace id and parent span id.
   *
   * @param parentSpanId - parent span id
   * @return a new transaction span
   */
  @NotNull
  private ISpan startChild(final @NotNull SpanId parentSpanId, final @NotNull String operation) {
    Objects.requireNonNull(parentSpanId, "parentSpanId is required");
    Objects.requireNonNull(operation, "operation is required");
    final Span span = new Span(root.getTraceId(), parentSpanId, this, operation, this.hub);
    this.children.add(span);
    return span;
  }

  @Override
  public void setName(final @Nullable String name) {
    this.root.setName(name);
  }

  @Override
  public @Nullable String getName() {
    return this.root.getName();
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return root.startChild(operation);
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation, final @Nullable String description) {
    return root.startChild(operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return root.toSentryTrace();
  }

  @Override
  public void finish() {
    this.finish(this.getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    root.finish(status);
    hub.captureTransaction(this);
  }

  @Override
  public void setOperation(final @NotNull String operation) {
    this.root.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.root.getOperation();
  }

  @Override
  public void setDescription(final @Nullable String description) {
    this.root.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.root.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    this.root.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.root.getStatus();
  }

  @Override
  public void setThrowable(final @Nullable Throwable throwable) {
    this.root.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return this.root.getThrowable();
  }

  @Override
  public @NotNull SpanContext getContext() {
    return this.root.getContext();
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.root.setTag(key, value);
  }

  @Override
  public @NotNull String getTag(final @NotNull String key) {
    return this.root.getTag(key);
  }

  @Override
  public boolean isFinished() {
    return this.root.isFinished();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return this.root.isSampled();
  }

  @Override
  public @Nullable ISpan getLatestActiveSpan() {
    final List<Span> spans = new ArrayList<>(this.children);
    if (!spans.isEmpty()) {
      for (int i = spans.size() - 1; i >= 0; i--) {
        if (!spans.get(i).isFinished()) {
          return spans.get(i);
        }
      }
    }
    return root.getLatestActiveSpan();
  }

  @NotNull Span getRoot() {
    return root;
  }
}
