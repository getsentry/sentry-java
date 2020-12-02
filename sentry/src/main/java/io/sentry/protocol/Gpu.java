package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class Gpu implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "gpu";

  /** The name of the graphics device. */
  private String name;
  /** The PCI identifier of the graphics device. */
  private Integer id;
  /** The PCI vendor identifier of the graphics device. */
  private Integer vendorId;
  /** The vendor name as reported by the graphics device. */
  private String vendorName;
  /** The total GPU memory available in Megabytes. */
  private Integer memorySize;
  /**
   * The device low-level API type.
   *
   * <p>Examples: `"Apple Metal"` or `"Direct3D11"`
   */
  private String apiType;
  /** Whether the GPU has multi-threaded rendering or not. */
  private Boolean multiThreadedRendering;
  /** The Version of the graphics device. */
  private String version;
  /** The Non-Power-Of-Two support. */
  private String npotSupport;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getVendorId() {
    return vendorId;
  }

  public void setVendorId(Integer vendorId) {
    this.vendorId = vendorId;
  }

  public String getVendorName() {
    return vendorName;
  }

  public void setVendorName(String vendorName) {
    this.vendorName = vendorName;
  }

  public Integer getMemorySize() {
    return memorySize;
  }

  public void setMemorySize(Integer memorySize) {
    this.memorySize = memorySize;
  }

  public String getApiType() {
    return apiType;
  }

  public void setApiType(String apiType) {
    this.apiType = apiType;
  }

  public Boolean isMultiThreadedRendering() {
    return multiThreadedRendering;
  }

  public void setMultiThreadedRendering(Boolean multiThreadedRendering) {
    this.multiThreadedRendering = multiThreadedRendering;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getNpotSupport() {
    return npotSupport;
  }

  public void setNpotSupport(String npotSupport) {
    this.npotSupport = npotSupport;
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
