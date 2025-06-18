package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OperatingSystem implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "os";

  /** Name of the operating system. */
  private @Nullable String name;

  /** Version of the operating system. */
  private @Nullable String version;

  /**
   * Unprocessed operating system info.
   *
   * <p>An unprocessed description string obtained by the operating system. For some well-known
   * runtimes, Sentry will attempt to parse `name` and `version` from this string, if they are not
   * explicitly given.
   */
  private @Nullable String rawDescription;

  /** Internal build number of the operating system. */
  private @Nullable String build;

  /**
   * Current kernel version.
   *
   * <p>This is typically the entire output of the `uname` syscall.
   */
  private @Nullable String kernelVersion;

  /** Indicator if the OS is rooted (mobile mostly). */
  private @Nullable Boolean rooted;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public OperatingSystem() {}

  OperatingSystem(final @NotNull OperatingSystem operatingSystem) {
    this.name = operatingSystem.name;
    this.version = operatingSystem.version;
    this.rawDescription = operatingSystem.rawDescription;
    this.build = operatingSystem.build;
    this.kernelVersion = operatingSystem.kernelVersion;
    this.rooted = operatingSystem.rooted;
    this.unknown = CollectionUtils.newConcurrentHashMap(operatingSystem.unknown);
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(final @Nullable String name) {
    this.name = name;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(final @Nullable String version) {
    this.version = version;
  }

  public @Nullable String getRawDescription() {
    return rawDescription;
  }

  public void setRawDescription(final @Nullable String rawDescription) {
    this.rawDescription = rawDescription;
  }

  public @Nullable String getBuild() {
    return build;
  }

  public void setBuild(final @Nullable String build) {
    this.build = build;
  }

  public @Nullable String getKernelVersion() {
    return kernelVersion;
  }

  public void setKernelVersion(final @Nullable String kernelVersion) {
    this.kernelVersion = kernelVersion;
  }

  public @Nullable Boolean isRooted() {
    return rooted;
  }

  public void setRooted(final @Nullable Boolean rooted) {
    this.rooted = rooted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OperatingSystem that = (OperatingSystem) o;
    return Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(rawDescription, that.rawDescription)
        && Objects.equals(build, that.build)
        && Objects.equals(kernelVersion, that.kernelVersion)
        && Objects.equals(rooted, that.rooted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version, rawDescription, build, kernelVersion, rooted);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String VERSION = "version";
    public static final String RAW_DESCRIPTION = "raw_description";
    public static final String BUILD = "build";
    public static final String KERNEL_VERSION = "kernel_version";
    public static final String ROOTED = "rooted";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (version != null) {
      writer.name(JsonKeys.VERSION).value(version);
    }
    if (rawDescription != null) {
      writer.name(JsonKeys.RAW_DESCRIPTION).value(rawDescription);
    }
    if (build != null) {
      writer.name(JsonKeys.BUILD).value(build);
    }
    if (kernelVersion != null) {
      writer.name(JsonKeys.KERNEL_VERSION).value(kernelVersion);
    }
    if (rooted != null) {
      writer.name(JsonKeys.ROOTED).value(rooted);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<OperatingSystem> {

    @Override
    public @NotNull OperatingSystem deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      OperatingSystem operatingSystem = new OperatingSystem();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            operatingSystem.name = reader.nextStringOrNull();
            break;
          case JsonKeys.VERSION:
            operatingSystem.version = reader.nextStringOrNull();
            break;
          case JsonKeys.RAW_DESCRIPTION:
            operatingSystem.rawDescription = reader.nextStringOrNull();
            break;
          case JsonKeys.BUILD:
            operatingSystem.build = reader.nextStringOrNull();
            break;
          case JsonKeys.KERNEL_VERSION:
            operatingSystem.kernelVersion = reader.nextStringOrNull();
            break;
          case JsonKeys.ROOTED:
            operatingSystem.rooted = reader.nextBooleanOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      operatingSystem.setUnknown(unknown);
      reader.endObject();
      return operatingSystem;
    }
  }
}
