package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class Browser implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "browser";
  private String name;
  private String version;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
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

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
