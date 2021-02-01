package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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
  private @NotNull String name;
  /**
   * The version of the SDK. _Required._
   *
   * <p>It should have the [Semantic Versioning](https://semver.org/) format `MAJOR.MINOR.PATCH`,
   * without any prefix (no `v` or anything else in front of the major version number).
   *
   * <p>Examples: `0.1.0`, `1.0.0`, `4.3.12`
   */
  private @NotNull String version;
  /**
   * List of installed and loaded SDK packages. _Optional._
   *
   * <p>A list of packages that were installed as part of this SDK or the activated integrations.
   * Each package consists of a name in the format `source:identifier` and `version`. If the source
   * is a Git repository, the `source` should be `git`, the identifier should be a checkout link and
   * the version should be a Git reference (branch, tag or SHA).
   */
  private @Nullable List<SentryPackage> packages;
  /**
   * List of integrations that are enabled in the SDK. _Optional._
   *
   * <p>The list should have all enabled integrations, including default integrations. Default
   * integrations are included because different SDK releases may contain different default
   * integrations.
   */
  private @Nullable List<String> integrations;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SdkVersion(final @NotNull String name, final @NotNull String version) {
    this.name = Objects.requireNonNull(name, "name is required.");
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  /**
   * @deprecated
   *     <p>Use {@link SdkVersion#SdkVersion(String, String)} instead.
   */
  @Deprecated
  public SdkVersion() {
    this("", "");
  }

  public @NotNull String getVersion() {
    return version;
  }

  public void setVersion(final @NotNull String version) {
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required.");
  }

  public void addPackage(final @NotNull String name, final @NotNull String version) {
    Objects.requireNonNull(name, "name is required.");
    Objects.requireNonNull(version, "version is required.");

    SentryPackage newPackage = new SentryPackage(name, version);
    if (packages == null) {
      packages = new ArrayList<>();
    }
    packages.add(newPackage);
  }

  public void addIntegration(final @NotNull String integration) {
    Objects.requireNonNull(integration, "integration is required.");

    if (integrations == null) {
      integrations = new ArrayList<>();
    }
    integrations.add(integration);
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public @Nullable List<SentryPackage> getPackages() {
    return packages;
  }

  public @Nullable List<String> getIntegrations() {
    return integrations;
  }

  /**
   * Updates the Sdk name and version or create a new one with the given values
   *
   * @param sdk the SdkVersion object or null
   * @param name the sdk name
   * @param version the sdk version
   * @return the SdkVersion
   */
  public static @NotNull SdkVersion updateSdkVersion(
      @Nullable SdkVersion sdk, final @NotNull String name, final @NotNull String version) {
    Objects.requireNonNull(name, "name is required.");
    Objects.requireNonNull(version, "version is required.");

    if (sdk == null) {
      sdk = new SdkVersion(name, version);
    } else {
      sdk.setName(name);
      sdk.setVersion(version);
    }
    return sdk;
  }
}
