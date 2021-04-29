package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
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
public final class Mechanism implements IUnknownPropertiesConsumer {
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
    this.meta = meta;
  }

  public @Nullable Map<String, Object> getData() {
    return data;
  }

  public void setData(final @Nullable Map<String, Object> data) {
    this.data = data;
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

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
