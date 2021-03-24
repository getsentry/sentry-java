package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.Objects;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An installed and loaded package as part of the Sentry SDK. */
public final class SentryPackage implements IUnknownPropertiesConsumer {
  /** Name of the package. */
  private @NotNull String name;
  /** Version of the package. */
  private @NotNull String version;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SentryPackage(final @NotNull String name, final @NotNull String version) {
    this.name = Objects.requireNonNull(name, "name is required.");
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  /**
   * @deprecated
   *     <p>Use {@link SentryPackage#SentryPackage(String, String)} instead.
   */
  @Deprecated
  public SentryPackage() {
    this("", "");
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required.");
  }

  public @NotNull String getVersion() {
    return version;
  }

  public void setVersion(final @NotNull String version) {
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
