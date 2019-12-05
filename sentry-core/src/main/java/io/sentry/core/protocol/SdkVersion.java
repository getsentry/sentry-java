package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

public final class SdkVersion implements IUnknownPropertiesConsumer {
  private String name;
  private String version;
  private List<SentryPackage> packages;
  private List<String> integrations;

  @SuppressWarnings("unused")
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
    SentryPackage newPackage = new SentryPackage();
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

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
