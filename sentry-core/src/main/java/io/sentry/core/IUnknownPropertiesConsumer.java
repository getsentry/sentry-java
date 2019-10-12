package io.sentry.core;

import java.util.Map;

public interface IUnknownPropertiesConsumer {
  void acceptUnknownProperties(Map<String, Object> unknown);
}
