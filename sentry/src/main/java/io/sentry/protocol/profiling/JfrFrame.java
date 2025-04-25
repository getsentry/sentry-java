package io.sentry.protocol.profiling;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

import io.sentry.ILogger;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;

public final class JfrFrame implements JsonUnknown, JsonSerializable {
//  @JsonProperty("function")
  public @Nullable String function; // e.g., "com.example.MyClass.myMethod"

//  @JsonProperty("module")
  public @Nullable String module; // e.g., "com.example" (package name)

//  @JsonProperty("filename")
  public @Nullable String filename; // e.g., "MyClass.java"

//  @JsonProperty("lineno")
  public @Nullable Integer lineno; // Line number (nullable)

//  @JsonProperty("abs_path")
  public @Nullable String absPath; // Optional: Absolute path if available

  public static final class JsonKeys {
    public static final String FUNCTION = "function";
    public static final String MODULE = "module";
    public static final String FILENAME = "filename";
    public static final String LINE_NO = "lineno";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();

    if(function != null) {
      writer.name(JsonKeys.FUNCTION).value(logger, function);
    }
    if(module != null) {
      writer.name(JsonKeys.MODULE).value(logger, module);
    }
    if(filename != null) {
      writer.name(JsonKeys.FILENAME).value(logger, filename);
    }
    if(lineno != null) {
      writer.name(JsonKeys.LINE_NO).value(logger, lineno);
    }

    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return Map.of();
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {

  }

  // We need equals and hashCode for deduplication if we use Frame objects directly as map keys
  // However, it's safer to deduplicate based on the source ResolvedFrame or its components.
  // Let's assume we handle deduplication before creating these final Frame objects.
}
