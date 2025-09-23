package io.sentry;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SentryValues<T> {
  private final List<T> values;

  SentryValues(@Nullable List<T> values) {
    if (values == null) {
      values = new ArrayList<>(0);
    }
    this.values = new ArrayList<>(values);
  }

  public @NotNull List<T> getValues() {
    return values;
  }

  public static final class JsonKeys {
    public static final String VALUES = "values";
  }
}
