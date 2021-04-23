package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class Gpu implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "gpu";

  /** The name of the graphics device. */
  private @Nullable String name;
  /** The PCI identifier of the graphics device. */
  private @Nullable Integer id;
  /** The PCI vendor identifier of the graphics device. */
  private @Nullable Integer vendorId;
  /** The vendor name as reported by the graphics device. */
  private @Nullable String vendorName;
  /** The total GPU memory available in Megabytes. */
  private @Nullable Integer memorySize;
  /**
   * The device low-level API type.
   *
   * <p>Examples: `"Apple Metal"` or `"Direct3D11"`
   */
  private @Nullable String apiType;
  /** Whether the GPU has multi-threaded rendering or not. */
  private @Nullable Boolean multiThreadedRendering;
  /** The Version of the graphics device. */
  private @Nullable String version;
  /** The Non-Power-Of-Two support. */
  private @Nullable String npotSupport;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public @Nullable String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public @Nullable Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public @Nullable Integer getVendorId() {
    return vendorId;
  }

  public void setVendorId(Integer vendorId) {
    this.vendorId = vendorId;
  }

  public @Nullable String getVendorName() {
    return vendorName;
  }

  public void setVendorName(final @Nullable String vendorName) {
    this.vendorName = vendorName;
  }

  public @Nullable Integer getMemorySize() {
    return memorySize;
  }

  public void setMemorySize(final @Nullable Integer memorySize) {
    this.memorySize = memorySize;
  }

  public @Nullable String getApiType() {
    return apiType;
  }

  public void setApiType(final @Nullable String apiType) {
    this.apiType = apiType;
  }

  public @Nullable Boolean isMultiThreadedRendering() {
    return multiThreadedRendering;
  }

  public void setMultiThreadedRendering(final @Nullable Boolean multiThreadedRendering) {
    this.multiThreadedRendering = multiThreadedRendering;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(final @Nullable String version) {
    this.version = version;
  }

  public @Nullable String getNpotSupport() {
    return npotSupport;
  }

  public void setNpotSupport(final @Nullable String npotSupport) {
    this.npotSupport = npotSupport;
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
   * Clones a Gpu aka deep copy
   *
   * @return the cloned Gpu
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull Gpu clone() throws CloneNotSupportedException {
    final Gpu clone = (Gpu) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
