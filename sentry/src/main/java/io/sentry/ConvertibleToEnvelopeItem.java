package io.sentry;

/** An object that can be converted to a {@link SentryItem} as a part of {@link SentryEnvelope}. */
public interface ConvertibleToEnvelopeItem {
  SentryItemType sentryItemType();
}
