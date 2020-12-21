package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransaction implements ITransaction {

  @Override
  public void setName(@NotNull String name) {}

  @Override
  public Span startChild() {
    return new Span(SentryId.EMPTY_ID, SpanId.EMPTY_ID, this, NoOpHub.getInstance());
  }

  @Override
  public Span startChild(String operation, String description) {
    return startChild();
  }

  @Override
  public @NotNull Span startChild(@NotNull SpanId parentSpanId) {
    return startChild();
  }

  @Override
  public @NotNull Span startChild(
      @NotNull SpanId parentSpanId, @NotNull String operation, @NotNull String description) {
    return startChild();
  }

  @Override
  public void setRequest(Request request) {}

  @Override
  public Request getRequest() {
    return null;
  }

  @Override
  public Contexts getContexts() {
    return null;
  }

  @Override
  public void setContexts(Contexts contexts) {}

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public @NotNull List<Span> getSpans() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public @Nullable Span getLatestActiveSpan() {
    return null;
  }

  @Override
  public @Nullable SentryId getEventId() {
    return null;
  }

  @Override
  public @Nullable String getTransaction() {
    return null;
  }

  @Override
  public SentryTraceHeader toSentryTrace() {
    return null;
  }

  @Override
  public void finish() {}

  @Override
  public void setOperation(@Nullable String operation) {}

  @Override
  public void setDescription(@Nullable String description) {}

  @Override
  public void setStatus(@Nullable SpanStatus status) {}

  @Override
  public void setThrowable(@Nullable Throwable throwable) {}

  @Override
  public @Nullable Throwable getThrowable() {
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return startChild();
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {}
}
