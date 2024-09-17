package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The mechanism by which an exception was generated and handled.
 *
 * <p>The exception mechanism is an optional field residing in the [exception](#typedef-Exception).
 * It carries additional information about the way the exception was created on the target system.
 * This includes general exception values obtained from the operating system or runtime APIs, as
 * well as mechanism-specific values.
 */
public final class Mechanism implements JsonUnknown, JsonSerializable {
  private final transient @Nullable Thread thread;
  /**
   * Mechanism type (required).
   *
   * <p>Required unique identifier of this mechanism determining rendering and processing of the
   * mechanism data.
   *
   * <p>In the Python SDK this is merely the name of the framework integration that produced the
   * exception, while for native it is e.g. `"minidump"` or `"applecrashreport"`.
   */
  private @Nullable String type;
  /**
   * Optional human-readable description of the error mechanism.
   *
   * <p>May include a possible hint on how to solve this error.
   */
  private @Nullable String description;
  /** Link to online resources describing this error. */
  private @Nullable String helpLink;
  /**
   * Flag indicating whether this exception was handled.
   *
   * <p>This is a best-effort guess at whether the exception was handled by user code or not. For
   * example:
   *
   * <p>- Exceptions leading to a 500 Internal Server Error or to a hard process crash are
   * `handled=false`, as the SDK typically has an integration that automatically captures the error.
   *
   * <p>- Exceptions captured using `capture_exception` (called from user code) are `handled=true`
   * as the user explicitly captured the exception (and therefore kind of handled it)
   */
  private @Nullable Boolean handled;
  /** Operating system or runtime meta information. */
  private @Nullable Map<String, Object> meta;
  /**
   * Arbitrary extra data that might help the user understand the error thrown by this mechanism.
   */
  private @Nullable Map<String, Object> data;
  /**
   * If this is set then the exception is not a real exception but some form of synthetic error for
   * instance from a signal handler, a hard segfault or similar where type and value are not useful
   * for grouping or display purposes.
   */
  private @Nullable Boolean synthetic;
  /**
   * Exception ID. Used. e.g. for exception groups to build a hierarchy. This is referenced as
   * parent by child exceptions which for Java SDK means Throwable.getSuppressed().
   */
  private @Nullable Integer exceptionId;
  /** Parent exception ID. Used e.g. for exception groups to build a hierarchy. */
  private @Nullable Integer parentId;
  /**
   * Whether this is a group of exceptions. For Java SDK this means there were suppressed
   * exceptions.
   */
  private @Nullable Boolean exceptionGroup;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public Mechanism() {
    this(null);
  }

  public Mechanism(final @Nullable Thread thread) {
    this.thread = thread;
  }

  public @Nullable String getType() {
    return type;
  }

  public void setType(final @Nullable String type) {
    this.type = type;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(final @Nullable String description) {
    this.description = description;
  }

  public @Nullable String getHelpLink() {
    return helpLink;
  }

  public void setHelpLink(final @Nullable String helpLink) {
    this.helpLink = helpLink;
  }

  public @Nullable Boolean isHandled() {
    return handled;
  }

  public void setHandled(final @Nullable Boolean handled) {
    this.handled = handled;
  }

  public @Nullable Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(final @Nullable Map<String, Object> meta) {
    this.meta = CollectionUtils.newHashMap(meta);
  }

  public @Nullable Map<String, Object> getData() {
    return data;
  }

  public void setData(final @Nullable Map<String, Object> data) {
    this.data = CollectionUtils.newHashMap(data);
  }

  @Nullable
  Thread getThread() {
    return thread;
  }

  public @Nullable Boolean getSynthetic() {
    return synthetic;
  }

  public void setSynthetic(final @Nullable Boolean synthetic) {
    this.synthetic = synthetic;
  }

  public @Nullable Integer getExceptionId() {
    return exceptionId;
  }

  public void setExceptionId(final @Nullable Integer exceptionId) {
    this.exceptionId = exceptionId;
  }

  public @Nullable Integer getParentId() {
    return parentId;
  }

  public void setParentId(final @Nullable Integer parentId) {
    this.parentId = parentId;
  }

  public @Nullable Boolean isExceptionGroup() {
    return exceptionGroup;
  }

  public void setExceptionGroup(final @Nullable Boolean exceptionGroup) {
    this.exceptionGroup = exceptionGroup;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String DESCRIPTION = "description";
    public static final String HELP_LINK = "help_link";
    public static final String HANDLED = "handled";
    public static final String META = "meta";
    public static final String DATA = "data";
    public static final String SYNTHETIC = "synthetic";
    public static final String EXCEPTION_ID = "exception_id";
    public static final String PARENT_ID = "parent_id";
    public static final String IS_EXCEPTION_GROUP = "is_exception_group";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    if (description != null) {
      writer.name(JsonKeys.DESCRIPTION).value(description);
    }
    if (helpLink != null) {
      writer.name(JsonKeys.HELP_LINK).value(helpLink);
    }
    if (handled != null) {
      writer.name(JsonKeys.HANDLED).value(handled);
    }
    if (meta != null) {
      writer.name(JsonKeys.META).value(logger, meta);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
    }
    if (synthetic != null) {
      writer.name(JsonKeys.SYNTHETIC).value(synthetic);
    }
    if (exceptionId != null) {
      writer.name(JsonKeys.EXCEPTION_ID).value(logger, exceptionId);
    }
    if (parentId != null) {
      writer.name(JsonKeys.PARENT_ID).value(logger, parentId);
    }
    if (exceptionGroup != null) {
      writer.name(JsonKeys.IS_EXCEPTION_GROUP).value(exceptionGroup);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<Mechanism> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Mechanism deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      Mechanism mechanism = new Mechanism();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            mechanism.type = reader.nextStringOrNull();
            break;
          case JsonKeys.DESCRIPTION:
            mechanism.description = reader.nextStringOrNull();
            break;
          case JsonKeys.HELP_LINK:
            mechanism.helpLink = reader.nextStringOrNull();
            break;
          case JsonKeys.HANDLED:
            mechanism.handled = reader.nextBooleanOrNull();
            break;
          case JsonKeys.META:
            mechanism.meta =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, Object>) reader.nextObjectOrNull());
            break;
          case JsonKeys.DATA:
            mechanism.data =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, Object>) reader.nextObjectOrNull());
            break;
          case JsonKeys.SYNTHETIC:
            mechanism.synthetic = reader.nextBooleanOrNull();
            break;
          case JsonKeys.EXCEPTION_ID:
            mechanism.exceptionId = reader.nextIntegerOrNull();
            break;
          case JsonKeys.PARENT_ID:
            mechanism.parentId = reader.nextIntegerOrNull();
            break;
          case JsonKeys.IS_EXCEPTION_GROUP:
            mechanism.exceptionGroup = reader.nextBooleanOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();
      mechanism.setUnknown(unknown);
      return mechanism;
    }
  }
}
