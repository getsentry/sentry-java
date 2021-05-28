package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class Browser implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "browser";
  /** Display name of the browser application. */
  private @Nullable String name;
  /** Version string of the browser. */
  private @Nullable String version;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public Browser() {}

  Browser(final @NotNull Browser browser) {
    this.name = browser.name;
    this.version = browser.version;
    this.unknown = CollectionUtils.newConcurrentHashMap(browser.unknown);
  }

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

  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Clones a Browser aka deep copy
   *
   * @return the cloned Browser
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull Browser clone() throws CloneNotSupportedException {
    final Browser clone = (Browser) super.clone();

    clone.unknown = CollectionUtils.newConcurrentHashMap(unknown);

    return clone;
  }
}
