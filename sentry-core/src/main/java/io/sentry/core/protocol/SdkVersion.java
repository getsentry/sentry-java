package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SdkVersion implements IUnknownPropertiesConsumer {
  private String name;
  private String version;
  private List<Package> packages = new CopyOnWriteArrayList<>();
  private Map<String, Object> unknown;

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Package> getPackages() {
    if (packages == null) {
      return new CopyOnWriteArrayList<>();
    }
    return packages;
  }

  public void addPackage(String name, String version) {
    Package newPackage = new Package();
    newPackage.setName(name);
    newPackage.setVersion(version);
    packages.add(newPackage);
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
