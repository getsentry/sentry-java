package io.sentry;

import com.jakewharton.nopen.annotation.Open;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

@Open
class SpanContext {
  /** Short code identifying the type of operation the span is measuring. */
  protected @Nullable String op;

  /**
   * Longer description of the span's operation, which uniquely identifies the span but is
   * consistent across instances of the span.
   */
  protected @Nullable String description;

  /** Describes the status of the Transaction. */
  protected @Nullable SpanStatus status;

  /** A map or list of tags for this event. Each tag must be less than 200 characters. */
  protected @Nullable Map<String, String> tags;

  public void setOp(String op) {
    this.op = op;
  }

  public void setTag(final String name, final String value) {
    if (this.tags == null) {
      this.tags = new ConcurrentHashMap<>();
    }
    this.tags.put(name, value);
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setStatus(SpanStatus status) {
    this.status = status;
  }

  public String getOp() {
    return op;
  }

  public String getDescription() {
    return description;
  }

  public SpanStatus getStatus() {
    return status;
  }

  public Map<String, String> getTags() {
    return tags;
  }
}
