package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

interface JsonElementSerializer<T> {
  void serialize(T src, @NotNull JsonObjectWriter writer) throws IOException;
}
