package io.sentry;

import io.sentry.protocol.SentryId;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProfilingTraceData {
  private final @NotNull File traceFile;
  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;

  public ProfilingTraceData(
      @NotNull File traceFile, @NotNull SentryId traceId, @NotNull SpanId spanId) {
    this.traceFile = traceFile;
    this.traceId = traceId;
    this.spanId = spanId;
  }

  @NotNull
  public File getTraceFile() {
    return traceFile;
  }

  @NotNull
  public SentryId getTraceId() {
    return traceId;
  }

  @NotNull
  public SpanId getSpanId() {
    return spanId;
  }
}
