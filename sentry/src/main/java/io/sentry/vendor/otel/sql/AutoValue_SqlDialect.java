package io.sentry.vendor.otel.sql;

import org.jetbrains.annotations.NotNull;

/**
 * What AutoValue would generate for the vendored {@link SqlDialect}: field, constructor, getter and
 * builder. Hand-written so that {@code sentry} needs no annotation processor. A property added or
 * removed upstream breaks compilation here, so a sync cannot drift silently.
 */
final class AutoValue_SqlDialect extends SqlDialect {

  private final boolean doubleQuotesAreIdentifiers;

  private AutoValue_SqlDialect(final boolean doubleQuotesAreIdentifiers) {
    this.doubleQuotesAreIdentifiers = doubleQuotesAreIdentifiers;
  }

  @Override
  boolean doubleQuotesAreIdentifiers() {
    return doubleQuotesAreIdentifiers;
  }

  static final class Builder extends SqlDialect.Builder {

    private boolean doubleQuotesAreIdentifiers;

    Builder() {}

    @Override
    public @NotNull SqlDialect.Builder setDoubleQuotesAreIdentifiers(
        final boolean doubleQuotesAreIdentifiers) {
      this.doubleQuotesAreIdentifiers = doubleQuotesAreIdentifiers;
      return this;
    }

    // This class stands in for AutoValue's output, so referring to AutoValue_SqlDialect is its job.
    @SuppressWarnings("AutoValueSubclassLeaked")
    @Override
    public @NotNull SqlDialect build() {
      return new AutoValue_SqlDialect(doubleQuotesAreIdentifiers);
    }
  }
}
