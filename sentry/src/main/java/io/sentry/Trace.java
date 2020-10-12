package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Trace implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "trace";

  private SentryId traceId;
  private SpanId spanId;

  /**
   * Short code identifying the type of operation the span is measuring.
   */
  private @Nullable String op;

  /**
   * Longer description of the span's operation, which uniquely identifies the span but is consistent across instances of the span.
   */
  private @Nullable String description;

  /**
   * Describes the status of the Transaction.
   */
  private @Nullable SpanStatus status;

  /**
   * A map or list of tags for this event. Each tag must be less than 200 characters.
   */
  private @Nullable Map<String, String> tags;

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

  public String getOp() {
    return op;
  }

  public void setOp(String op) {
    this.op = op;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SpanStatus getStatus() {
    return status;
  }

  public void setStatus(SpanStatus status) {
    this.status = status;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public void setTags(Map<String, String> tags) {
    this.tags = tags;
  }

  public Map<String, Object> getUnknown() {
    return unknown;
  }

  public void setUnknown(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public void setTag(final String name, final String value) {
    if (this.tags == null) {
      this.tags = new ConcurrentHashMap<>();
    }
    this.tags.put(name, value);
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    final Trace clone = (Trace) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);
    clone.tags = CollectionUtils.shallowCopy(tags);

    return clone;
  }
}
