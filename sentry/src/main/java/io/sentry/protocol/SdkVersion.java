package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SDK Interface describes the Sentry SDK and its configuration used to capture and transmit an
 * event.
 */
public final class SdkVersion implements JsonUnknown, JsonSerializable {
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
  private @Nullable Set<SentryPackage> deserializedPackages;
  /**
   * List of integrations that are enabled in the SDK. _Optional._
   *
   * <p>The list should have all enabled integrations, including default integrations. Default
   * integrations are included because different SDK releases may contain different default
   * integrations.
   */
  private @Nullable Set<String> deserializedIntegrations;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SdkVersion(final @NotNull String name, final @NotNull String version) {
    this.name = Objects.requireNonNull(name, "name is required.");
    this.version = Objects.requireNonNull(version, "version is required.");
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
    SentryIntegrationPackageStorage.getInstance().addPackage(name, version);
  }

  public void addIntegration(final @NotNull String integration) {
    SentryIntegrationPackageStorage.getInstance().addIntegration(integration);
  }

  public @NotNull Set<SentryPackage> getPackageSet() {
    return deserializedPackages != null
        ? deserializedPackages
        : SentryIntegrationPackageStorage.getInstance().getPackages();
  }

  public @NotNull Set<String> getIntegrationSet() {
    return deserializedIntegrations != null
        ? deserializedIntegrations
        : SentryIntegrationPackageStorage.getInstance().getIntegrations();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SdkVersion that = (SdkVersion) o;
    return name.equals(that.name) && version.equals(that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version);
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String VERSION = "version";
    public static final String PACKAGES = "packages";
    public static final String INTEGRATIONS = "integrations";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.NAME).value(name);
    writer.name(JsonKeys.VERSION).value(version);
    Set<SentryPackage> packages = getPackageSet();
    Set<String> integrations = getIntegrationSet();
    if (!packages.isEmpty()) {
      writer.name(JsonKeys.PACKAGES).value(logger, packages);
    }
    if (!integrations.isEmpty()) {
      writer.name(JsonKeys.INTEGRATIONS).value(logger, integrations);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  @SuppressWarnings("unchecked")
  public static final class Deserializer implements JsonDeserializer<SdkVersion> {
    @Override
    public @NotNull SdkVersion deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      String name = null;
      String version = null;
      List<SentryPackage> packages = new ArrayList<>();
      List<String> integrations = new ArrayList<>();
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            name = reader.nextString();
            break;
          case JsonKeys.VERSION:
            version = reader.nextString();
            break;
          case JsonKeys.PACKAGES:
            List<SentryPackage> deserializedPackages =
                reader.nextListOrNull(logger, new SentryPackage.Deserializer());
            if (deserializedPackages != null) {
              packages.addAll(deserializedPackages);
            }
            break;
          case JsonKeys.INTEGRATIONS:
            List<String> deserializedIntegrations = (List<String>) reader.nextObjectOrNull();
            if (deserializedIntegrations != null) {
              integrations.addAll(deserializedIntegrations);
            }
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (name == null) {
        String message = "Missing required field \"" + JsonKeys.NAME + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }
      if (version == null) {
        String message = "Missing required field \"" + JsonKeys.VERSION + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      SdkVersion sdkVersion = new SdkVersion(name, version);
      sdkVersion.deserializedPackages = new CopyOnWriteArraySet<>(packages);
      sdkVersion.deserializedIntegrations = new CopyOnWriteArraySet<>(integrations);

      sdkVersion.setUnknown(unknown);
      return sdkVersion;
    }
  }
}
