package io.sentry.core;

import java.util.ArrayList;
import java.util.List;

final class SentryValues<T> {
  private List<T> values;

  public SentryValues(List<T> values) {
    if (values == null) {
      values = new ArrayList<>(0);
    }
    this.values = values;
  }

  public List<T> getValues() {
    return values;
  }
}
