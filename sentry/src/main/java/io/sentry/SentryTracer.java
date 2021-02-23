package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryTracer implements ISpan {
  private final Span root;
  private final List<Span> children = new CopyOnWriteArrayList<>();
  private final IHub hub;

  public SentryTracer(TransactionContext context, IHub hub) {
    this.root = new Span(context, this, hub);
    this.root.setName(context.getName());
    this.hub = hub;
  }

  public List<Span> getChildren() {
    return children;
  }

  public Date getStartTimestamp() {
    return this.root.getStartTimestamp();
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
  public void setName(String name) {
    this.root.setName(name);
  }

  @Override
  public String getName() {
    return this.root.getName();
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return root.startChild(operation);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    return root.startChild(operation, description);
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return root.toSentryTrace();
  }

  @Override
  public void finish() {
    this.finish(SpanStatus.OK);
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    root.setStatus(status);
    hub.captureTransaction(this);
  }

  @Override
  public void setOperation(@Nullable String operation) {
    this.root.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return this.root.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    this.root.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.root.getDescription();
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    this.root.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return this.root.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    this.root.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return this.root.getThrowable();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return this.root.getSpanContext();
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    this.root.setTag(key, value);
  }

  @Override
  public @NotNull String getTag(@NotNull String key) {
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
  public @NotNull ISpan getLatestActiveSpan() {
    final List<Span> spans = new ArrayList<>(this.children);
    if (!spans.isEmpty()) {
      for (int i = spans.size() - 1; i >= 0; i--) {
        if (!spans.get(i).isFinished()) {
          return spans.get(i);
        }
      }
    }
    return root;
  }

  public Span getRoot() {
    return root;
  }
}
