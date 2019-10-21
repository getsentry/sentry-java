package io.sentry.core.protocol;

public class OperatingSystem {
  static final String TYPE = "os";

  private String name;
  private String version;
  private String rawDescription;
  private String build;
  private String kernelVersion;
  private Boolean rooted;

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
}
