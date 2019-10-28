package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SdkVersion implements IUnknownPropertiesConsumer {
  private String name;
  private String version;
  private List<Package> packages;
  private List<String> integrations;
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

  public void addPackage(String name, String version) {
    Package newPackage = new Package();
    newPackage.setName(name);
    newPackage.setVersion(version);
    if (packages == null) {
      packages = new ArrayList<>();
    }
    packages.add(newPackage);
  }

  public void addIntegration(String integration) {
    if (integrations == null) {
      integrations = new ArrayList<>();
    }
    integrations.add(integration);
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
