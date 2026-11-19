package io.sentry;

/** Values supported by the {@code sentry.kind} span data attribute. */
public final class SentrySpanKind {
  public static final String CLIENT = "client";
  public static final String SERVER = "server";
  public static final String PRODUCER = "producer";
  public static final String CONSUMER = "consumer";
  public static final String INTERNAL = "internal";

  private SentrySpanKind() {}
}
