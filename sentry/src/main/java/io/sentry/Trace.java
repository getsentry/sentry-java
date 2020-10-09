package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Trace implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "trace";

  private SentryId traceId;
  private SpanId spanId;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

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

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    final Trace clone = (Trace) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
