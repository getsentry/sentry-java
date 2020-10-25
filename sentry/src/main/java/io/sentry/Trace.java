package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public final class Trace extends SpanContext implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "trace";

  /** Determines which trace the Span belongs to. */
  private SentryId traceId;

  /** The span id. */
  private SpanId spanId;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public Trace(final @NotNull SentryId traceId, final @NotNull SpanId spanId) {
    this.traceId = traceId;
    this.spanId = spanId;
  }

  public Trace() {
    this(new SentryId(), new SpanId());
  }

  public SentryId getTraceId() {
    return traceId;
  }

  public void setTraceId(SentryId traceId) {
    this.traceId = traceId;
  }

  public SpanId getSpanId() {
    return spanId;
  }

  public void setSpanId(SpanId spanId) {
    this.spanId = spanId;
  }

  public Map<String, Object> getUnknown() {
    return unknown;
  }

  public void setUnknown(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  @Override
  protected Trace clone() throws CloneNotSupportedException {
    final Trace clone = (Trace) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);
    clone.tags = CollectionUtils.shallowCopy(tags);

    return clone;
  }
}
