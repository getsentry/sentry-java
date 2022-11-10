package io.sentry.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.sentry.Sentry;

@SuppressWarnings("CatchAndPrintStackTrace")
public final class SentrySpanProcessor implements SpanProcessor {

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    System.out.println(
        "hello from onStart " + Thread.currentThread().getId() + Sentry.getCurrentHub().toString());
    // TODO start
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    System.out.println(
        "hello from onEnd" + Thread.currentThread().getId() + Sentry.getCurrentHub().toString());
    // TODO end
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }
}
