package io.sentry.protocol;

public class Gpu {
  static final String TYPE = "gpu";

  private String name;
  private Integer id;
  private Integer vendorId;
  private String vendorName;
  private Integer memorySize;
  private String apiType;
  private Boolean multiThreadedRendering;
  private String version;
  private String npotSupport;

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

  public Boolean getMultiThreadedRendering() {
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
}
