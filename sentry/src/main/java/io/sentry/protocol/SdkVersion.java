package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The SDK Interface describes the Sentry SDK and its configuration used to capture and transmit an
 * event.
 */
public final class SdkVersion implements IUnknownPropertiesConsumer {
  /**
   * Unique SDK name. _Required._
   *
   * <p>The name of the SDK. The format is `entity.ecosystem[.flavor]` where entity identifies the
   * developer of the SDK, ecosystem refers to the programming language or platform where the SDK is
   * to be used and the optional flavor is used to identify standalone SDKs that are part of a major
   * ecosystem.
   *
   * <p>Official Sentry SDKs use the entity `sentry`, as in `sentry.python` or
   * `sentry.javascript.react-native`. Please use a different entity for your own SDKs.
   */
  private String name;
  /**
   * The version of the SDK. _Required._
   *
   * <p>It should have the [Semantic Versioning](https://semver.org/) format `MAJOR.MINOR.PATCH`,
   * without any prefix (no `v` or anything else in front of the major version number).
   *
   * <p>Examples: `0.1.0`, `1.0.0`, `4.3.12`
   */
  private String version;
  /**
   * List of installed and loaded SDK packages. _Optional._
   *
   * <p>A list of packages that were installed as part of this SDK or the activated integrations.
   * Each package consists of a name in the format `source:identifier` and `version`. If the source
   * is a Git repository, the `source` should be `git`, the identifier should be a checkout link and
   * the version should be a Git reference (branch, tag or SHA).
   */
  private List<SentryPackage> packages;
  /**
   * List of integrations that are enabled in the SDK. _Optional._
   *
   * <p>The list should have all enabled integrations, including default integrations. Default
   * integrations are included because different SDK releases may contain different default
   * integrations.
   */
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

  public @Nullable List<SentryPackage> getPackages() {
    return packages;
  }

  public @Nullable List<String> getIntegrations() {
    return integrations;
  }
}
