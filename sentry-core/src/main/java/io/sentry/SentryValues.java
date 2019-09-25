package io.sentry;

import java.util.ArrayList;
import java.util.List;

class SentryValues<T> {
  private List<T> items;

  public SentryValues(List<T> items) {
    if (items == null) {
      items = new ArrayList<>(0);
    }
    this.items = items;
  }

  public List<T> getValues() {
    return items;
  }
}
