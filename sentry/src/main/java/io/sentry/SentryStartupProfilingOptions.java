package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class SentryStartupProfilingOptions implements JsonUnknown, JsonSerializable {

  boolean profileSampled;
  @Nullable Double profileSampleRate;
  boolean traceSampled;
  @Nullable Double traceSampleRate;
  @Nullable String profilingTracesDirPath;
  boolean isProfilingEnabled;
  int profilingTracesHz;

  private @Nullable Map<String, Object> unknown;

  @VisibleForTesting
  public SentryStartupProfilingOptions() {
    traceSampled = false;
    traceSampleRate = null;
    profileSampled = false;
    profileSampleRate = null;
    profilingTracesDirPath = null;
    isProfilingEnabled = false;
    profilingTracesHz = 0;
  }

  SentryStartupProfilingOptions(
      final @NotNull SentryOptions options,
      final @NotNull TracesSamplingDecision samplingDecision) {
    traceSampled = samplingDecision.getSampled();
    traceSampleRate = samplingDecision.getSampleRate();
    profileSampled = samplingDecision.getProfileSampled();
    profileSampleRate = samplingDecision.getProfileSampleRate();
    profilingTracesDirPath = options.getProfilingTracesDirPath();
    isProfilingEnabled = options.isProfilingEnabled();
    profilingTracesHz = options.getProfilingTracesHz();
  }

  public void setProfileSampled(final boolean profileSampled) {
    this.profileSampled = profileSampled;
  }

  public boolean isProfileSampled() {
    return profileSampled;
  }

  public void setProfileSampleRate(final @Nullable Double profileSampleRate) {
    this.profileSampleRate = profileSampleRate;
  }

  public @Nullable Double getProfileSampleRate() {
    return profileSampleRate;
  }

  public void setTraceSampled(final boolean traceSampled) {
    this.traceSampled = traceSampled;
  }

  public boolean isTraceSampled() {
    return traceSampled;
  }

  public void setTraceSampleRate(final @Nullable Double traceSampleRate) {
    this.traceSampleRate = traceSampleRate;
  }

  public @Nullable Double getTraceSampleRate() {
    return traceSampleRate;
  }

  public void setProfilingTracesDirPath(final @Nullable String profilingTracesDirPath) {
    this.profilingTracesDirPath = profilingTracesDirPath;
  }

  public @Nullable String getProfilingTracesDirPath() {
    return profilingTracesDirPath;
  }

  public void setProfilingEnabled(final boolean profilingEnabled) {
    isProfilingEnabled = profilingEnabled;
  }

  public boolean isProfilingEnabled() {
    return isProfilingEnabled;
  }

  public void setProfilingTracesHz(final int profilingTracesHz) {
    this.profilingTracesHz = profilingTracesHz;
  }

  public int getProfilingTracesHz() {
    return profilingTracesHz;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String PROFILE_SAMPLED = "profile_sampled";
    public static final String PROFILE_SAMPLE_RATE = "profile_sample_rate";
    public static final String TRACE_SAMPLED = "trace_sampled";
    public static final String TRACE_SAMPLE_RATE = "trace_sample_rate";
    public static final String PROFILING_TRACES_DIR_PATH = "profiling_traces_dir_path";
    public static final String IS_PROFILING_ENABLED = "is_profiling_enabled";
    public static final String PROFILING_TRACES_HZ = "profiling_traces_hz";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.PROFILE_SAMPLED).value(logger, profileSampled);
    writer.name(JsonKeys.PROFILE_SAMPLE_RATE).value(logger, profileSampleRate);
    writer.name(JsonKeys.TRACE_SAMPLED).value(logger, traceSampled);
    writer.name(JsonKeys.TRACE_SAMPLE_RATE).value(logger, traceSampleRate);
    writer.name(JsonKeys.PROFILING_TRACES_DIR_PATH).value(logger, profilingTracesDirPath);
    writer.name(JsonKeys.IS_PROFILING_ENABLED).value(logger, isProfilingEnabled);
    writer.name(JsonKeys.PROFILING_TRACES_HZ).value(logger, profilingTracesHz);

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

  public static final class Deserializer
      implements JsonDeserializer<SentryStartupProfilingOptions> {

    @Override
    public @NotNull SentryStartupProfilingOptions deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      SentryStartupProfilingOptions options = new SentryStartupProfilingOptions();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.PROFILE_SAMPLED:
            Boolean profileSampled = reader.nextBooleanOrNull();
            if (profileSampled != null) {
              options.profileSampled = profileSampled;
            }
            break;
          case JsonKeys.PROFILE_SAMPLE_RATE:
            Double profileSampleRate = reader.nextDoubleOrNull();
            if (profileSampleRate != null) {
              options.profileSampleRate = profileSampleRate;
            }
            break;
          case JsonKeys.TRACE_SAMPLED:
            Boolean traceSampled = reader.nextBooleanOrNull();
            if (traceSampled != null) {
              options.traceSampled = traceSampled;
            }
            break;
          case JsonKeys.TRACE_SAMPLE_RATE:
            Double traceSampleRate = reader.nextDoubleOrNull();
            if (traceSampleRate != null) {
              options.traceSampleRate = traceSampleRate;
            }
            break;
          case JsonKeys.PROFILING_TRACES_DIR_PATH:
            String profilingTracesDirPath = reader.nextStringOrNull();
            if (profilingTracesDirPath != null) {
              options.profilingTracesDirPath = profilingTracesDirPath;
            }
            break;
          case JsonKeys.IS_PROFILING_ENABLED:
            Boolean isProfilingEnabled = reader.nextBooleanOrNull();
            if (isProfilingEnabled != null) {
              options.isProfilingEnabled = isProfilingEnabled;
            }
            break;
          case JsonKeys.PROFILING_TRACES_HZ:
            Integer profilingTracesHz = reader.nextIntegerOrNull();
            if (profilingTracesHz != null) {
              options.profilingTracesHz = profilingTracesHz;
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
      options.setUnknown(unknown);
      reader.endObject();
      return options;
    }
  }
}
