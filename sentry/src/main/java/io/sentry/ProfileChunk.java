package io.sentry;

import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfileChunk implements JsonUnknown, JsonSerializable {
  public static final String PLATFORM_ANDROID = "android";
  public static final String PLATFORM_JAVA = "java";

  private @Nullable DebugMeta debugMeta;
  private @NotNull SentryId profilerId;
  private @NotNull SentryId chunkId;
  private @Nullable SdkVersion clientSdk;
  private final @NotNull Map<String, ProfileMeasurement> measurements;
  private @NotNull String platform;
  private @NotNull String release;
  private @Nullable String environment;
  private @NotNull String version;
  private double timestamp;

  private final @NotNull File traceFile;

  /** Profile trace encoded with Base64. */
  private @Nullable String sampledProfile = null;

  private @Nullable SentryProfile sentryProfile;

  private @Nullable Map<String, Object> unknown;

  public ProfileChunk() {
    this(
        SentryId.EMPTY_ID,
        SentryId.EMPTY_ID,
        new File("dummy"),
        new HashMap<>(),
        0.0,
        PLATFORM_ANDROID,
        SentryOptions.empty());
  }

  public ProfileChunk(
      final @NotNull SentryId profilerId,
      final @NotNull SentryId chunkId,
      final @NotNull File traceFile,
      final @NotNull Map<String, ProfileMeasurement> measurements,
      final @NotNull Double timestamp,
      final @NotNull String platform,
      final @NotNull SentryOptions options) {
    this.profilerId = profilerId;
    this.chunkId = chunkId;
    this.traceFile = traceFile;
    this.measurements = measurements;
    this.debugMeta = null;
    this.clientSdk = options.getSdkVersion();
    this.release = options.getRelease() != null ? options.getRelease() : "";
    this.environment = options.getEnvironment();
    this.platform = platform;
    this.version = "2";
    this.timestamp = timestamp;
  }

  public @NotNull Map<String, ProfileMeasurement> getMeasurements() {
    return measurements;
  }

  public @Nullable DebugMeta getDebugMeta() {
    return debugMeta;
  }

  public void setDebugMeta(final @Nullable DebugMeta debugMeta) {
    this.debugMeta = debugMeta;
  }

  public @Nullable SdkVersion getClientSdk() {
    return clientSdk;
  }

  public @NotNull SentryId getChunkId() {
    return chunkId;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public @NotNull String getPlatform() {
    return platform;
  }

  public @NotNull SentryId getProfilerId() {
    return profilerId;
  }

  public @NotNull String getRelease() {
    return release;
  }

  public @Nullable String getSampledProfile() {
    return sampledProfile;
  }

  public void setSampledProfile(final @Nullable String sampledProfile) {
    this.sampledProfile = sampledProfile;
  }

  public @NotNull File getTraceFile() {
    return traceFile;
  }

  public double getTimestamp() {
    return timestamp;
  }

  public @NotNull String getVersion() {
    return version;
  }

  public @Nullable SentryProfile getSentryProfile() {
    return sentryProfile;
  }

  public void setSentryProfile(@Nullable SentryProfile sentryProfile) {
    this.sentryProfile = sentryProfile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProfileChunk)) return false;
    ProfileChunk that = (ProfileChunk) o;
    return Objects.equals(debugMeta, that.debugMeta)
        && Objects.equals(profilerId, that.profilerId)
        && Objects.equals(chunkId, that.chunkId)
        && Objects.equals(clientSdk, that.clientSdk)
        && Objects.equals(measurements, that.measurements)
        && Objects.equals(platform, that.platform)
        && Objects.equals(release, that.release)
        && Objects.equals(environment, that.environment)
        && Objects.equals(version, that.version)
        && Objects.equals(sampledProfile, that.sampledProfile)
        && Objects.equals(unknown, that.unknown)
        && Objects.equals(sentryProfile, that.sentryProfile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        debugMeta,
        profilerId,
        chunkId,
        clientSdk,
        measurements,
        platform,
        release,
        environment,
        version,
        sampledProfile,
        sentryProfile,
        unknown);
  }

  public static final class Builder {
    private final @NotNull SentryId profilerId;
    private final @NotNull SentryId chunkId;
    private final @NotNull Map<String, ProfileMeasurement> measurements;
    private final @NotNull File traceFile;
    private final double timestamp;

    private final @NotNull String platform;

    public Builder(
        final @NotNull SentryId profilerId,
        final @NotNull SentryId chunkId,
        final @NotNull Map<String, ProfileMeasurement> measurements,
        final @NotNull File traceFile,
        final @NotNull SentryDate timestamp,
        final @NotNull String platform) {
      this.profilerId = profilerId;
      this.chunkId = chunkId;
      this.measurements = new ConcurrentHashMap<>(measurements);
      this.traceFile = traceFile;
      this.timestamp = DateUtils.nanosToSeconds(timestamp.nanoTimestamp());
      this.platform = platform;
    }

    public ProfileChunk build(SentryOptions options) {
      return new ProfileChunk(
          profilerId, chunkId, traceFile, measurements, timestamp, platform, options);
    }
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String DEBUG_META = "debug_meta";
    public static final String PROFILER_ID = "profiler_id";
    public static final String CHUNK_ID = "chunk_id";
    public static final String CLIENT_SDK = "client_sdk";
    public static final String MEASUREMENTS = "measurements";
    public static final String PLATFORM = "platform";
    public static final String RELEASE = "release";
    public static final String ENVIRONMENT = "environment";
    public static final String VERSION = "version";
    public static final String SAMPLED_PROFILE = "sampled_profile";
    public static final String TIMESTAMP = "timestamp";
    public static final String SENTRY_PROFILE = "profile";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (debugMeta != null) {
      writer.name(JsonKeys.DEBUG_META).value(logger, debugMeta);
    }
    writer.name(JsonKeys.PROFILER_ID).value(logger, profilerId);
    writer.name(JsonKeys.CHUNK_ID).value(logger, chunkId);
    if (clientSdk != null) {
      writer.name(JsonKeys.CLIENT_SDK).value(logger, clientSdk);
    }
    if (!measurements.isEmpty()) {
      // Measurements can be a very long list which will make it hard to read in logs, so we don't
      // indent it
      final String prevIndent = writer.getIndent();
      writer.setIndent("");
      writer.name(JsonKeys.MEASUREMENTS).value(logger, measurements);
      writer.setIndent(prevIndent);
    }
    writer.name(JsonKeys.PLATFORM).value(logger, platform);
    writer.name(JsonKeys.RELEASE).value(logger, release);
    if (environment != null) {
      writer.name(JsonKeys.ENVIRONMENT).value(logger, environment);
    }
    writer.name(JsonKeys.VERSION).value(logger, version);
    if (sampledProfile != null) {
      writer.name(JsonKeys.SAMPLED_PROFILE).value(logger, sampledProfile);
    }
    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    if (sentryProfile != null) {
      writer.name(JsonKeys.SENTRY_PROFILE).value(logger, sentryProfile);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  private @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
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

  public static final class Deserializer implements JsonDeserializer<ProfileChunk> {

    @Override
    public @NotNull ProfileChunk deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      ProfileChunk data = new ProfileChunk();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.DEBUG_META:
            DebugMeta debugMeta = reader.nextOrNull(logger, new DebugMeta.Deserializer());
            if (debugMeta != null) {
              data.debugMeta = debugMeta;
            }
            break;
          case JsonKeys.PROFILER_ID:
            SentryId profilerId = reader.nextOrNull(logger, new SentryId.Deserializer());
            if (profilerId != null) {
              data.profilerId = profilerId;
            }
            break;
          case JsonKeys.CHUNK_ID:
            SentryId chunkId = reader.nextOrNull(logger, new SentryId.Deserializer());
            if (chunkId != null) {
              data.chunkId = chunkId;
            }
            break;
          case JsonKeys.CLIENT_SDK:
            SdkVersion clientSdk = reader.nextOrNull(logger, new SdkVersion.Deserializer());
            if (clientSdk != null) {
              data.clientSdk = clientSdk;
            }
            break;
          case JsonKeys.MEASUREMENTS:
            Map<String, ProfileMeasurement> measurements =
                reader.nextMapOrNull(logger, new ProfileMeasurement.Deserializer());
            if (measurements != null) {
              data.measurements.putAll(measurements);
            }
            break;
          case JsonKeys.PLATFORM:
            String platform = reader.nextStringOrNull();
            if (platform != null) {
              data.platform = platform;
            }
            break;
          case JsonKeys.RELEASE:
            String release = reader.nextStringOrNull();
            if (release != null) {
              data.release = release;
            }
            break;
          case JsonKeys.ENVIRONMENT:
            String environment = reader.nextStringOrNull();
            if (environment != null) {
              data.environment = environment;
            }
            break;
          case JsonKeys.VERSION:
            String version = reader.nextStringOrNull();
            if (version != null) {
              data.version = version;
            }
            break;
          case JsonKeys.SAMPLED_PROFILE:
            String sampledProfile = reader.nextStringOrNull();
            if (sampledProfile != null) {
              data.sampledProfile = sampledProfile;
            }
            break;
          case JsonKeys.TIMESTAMP:
            Double timestamp = reader.nextDoubleOrNull();
            if (timestamp != null) {
              data.timestamp = timestamp;
            }
            break;
          case JsonKeys.SENTRY_PROFILE:
            SentryProfile sentryProfile =
                reader.nextOrNull(logger, new SentryProfile.Deserializer());
            if (sentryProfile != null) {
              data.sentryProfile = sentryProfile;
            }
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      data.setUnknown(unknown);
      reader.endObject();
      return data;
    }
  }
}
