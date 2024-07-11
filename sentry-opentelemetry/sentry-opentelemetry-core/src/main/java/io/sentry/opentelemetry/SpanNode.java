package io.sentry.opentelemetry;

import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanNode {
  private final @NotNull String id;
  private @Nullable SpanData span;
  private @Nullable SpanNode parentNode;
  private @NotNull List<SpanNode> children = new CopyOnWriteArrayList<>();

  public SpanNode(final @NotNull String spanId) {
    this.id = spanId;
  }

  public @NotNull String getId() {
    return id;
  }

  public @Nullable SpanData getSpan() {
    return span;
  }

  public void setSpan(final @Nullable SpanData span) {
    this.span = span;
  }

  public @Nullable SpanNode getParentNode() {
    return parentNode;
  }

  public void setParentNode(final @Nullable SpanNode parentNode) {
    this.parentNode = parentNode;
  }

  public @NotNull List<SpanNode> getChildren() {
    return children;
  }

  public void addChildren(final @Nullable List<SpanNode> children) {
    if (children != null) {
      this.children.addAll(children);
    }
  }

  public void addChild(final @Nullable SpanNode child) {
    if (child != null) {
      this.children.add(child);
    }
  }
}
