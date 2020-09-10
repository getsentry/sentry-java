package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class OperatingSystem implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "os";

  private String name;
  private String version;
  private String rawDescription;
  private String build;
  private String kernelVersion;
  private Boolean rooted;

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

  public String getRawDescription() {
    return rawDescription;
  }

  public void setRawDescription(String rawDescription) {
    this.rawDescription = rawDescription;
  }

  public String getBuild() {
    return build;
  }

  public void setBuild(String build) {
    this.build = build;
  }

  public String getKernelVersion() {
    return kernelVersion;
  }

  public void setKernelVersion(String kernelVersion) {
    this.kernelVersion = kernelVersion;
  }

  public Boolean isRooted() {
    return rooted;
  }

  public void setRooted(Boolean rooted) {
    this.rooted = rooted;
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
