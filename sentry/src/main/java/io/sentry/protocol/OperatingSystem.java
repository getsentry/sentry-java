package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class OperatingSystem implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "os";

  /** Name of the operating system. */
  private @Nullable String name;
  /** Version of the operating system. */
  private @Nullable String version;
  /**
   * Unprocessed operating system info.
   *
   * <p>An unprocessed description string obtained by the operating system. For some well-known
   * runtimes, Sentry will attempt to parse `name` and `version` from this string, if they are not
   * explicitly given.
   */
  private @Nullable String rawDescription;
  /** Internal build number of the operating system. */
  private @Nullable String build;
  /**
   * Current kernel version.
   *
   * <p>This is typically the entire output of the `uname` syscall.
   */
  private @Nullable String kernelVersion;
  /** Indicator if the OS is rooted (mobile mostly). */
  private @Nullable Boolean rooted;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public @Nullable String getName() {
    return name;
  }

  public void setName(final @Nullable String name) {
    this.name = name;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(final @Nullable String version) {
    this.version = version;
  }

  public @Nullable String getRawDescription() {
    return rawDescription;
  }

  public void setRawDescription(final @Nullable String rawDescription) {
    this.rawDescription = rawDescription;
  }

  public @Nullable String getBuild() {
    return build;
  }

  public void setBuild(final @Nullable String build) {
    this.build = build;
  }

  public @Nullable String getKernelVersion() {
    return kernelVersion;
  }

  public void setKernelVersion(final @Nullable String kernelVersion) {
    this.kernelVersion = kernelVersion;
  }

  public @Nullable Boolean isRooted() {
    return rooted;
  }

  public void setRooted(final @Nullable Boolean rooted) {
    this.rooted = rooted;
  }

  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, @NotNull Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Clones an OperatingSystem aka deep copy
   *
   * @return the cloned OperatingSystem
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull OperatingSystem clone() throws CloneNotSupportedException {
    final OperatingSystem clone = (OperatingSystem) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
