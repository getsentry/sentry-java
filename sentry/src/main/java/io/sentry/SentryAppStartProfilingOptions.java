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
public final class SentryAppStartProfilingOptions implements JsonUnknown, JsonSerializable {

  boolean profileSampled;
  @Nullable Double profileSampleRate;
  boolean traceSampled;
  @Nullable Double traceSampleRate;
  @Nullable String profilingTracesDirPath;
  boolean isProfilingEnabled;
  boolean isContinuousProfilingEnabled;
  int profilingTracesHz;
  boolean continuousProfileSampled;

  private @Nullable Map<String, Object> unknown;

  @VisibleForTesting
  public SentryAppStartProfilingOptions() {
    traceSampled = false;
    traceSampleRate = null;
    profileSampled = false;
    profileSampleRate = null;
    continuousProfileSampled = false;
    profilingTracesDirPath = null;
    isProfilingEnabled = false;
    isContinuousProfilingEnabled = false;
    profilingTracesHz = 0;
  }

  SentryAppStartProfilingOptions(
      final @NotNull SentryOptions options,
      final @NotNull TracesSamplingDecision samplingDecision) {
    traceSampled = samplingDecision.getSampled();
    traceSampleRate = samplingDecision.getSampleRate();
    profileSampled = samplingDecision.getProfileSampled();
    profileSampleRate = samplingDecision.getProfileSampleRate();
    continuousProfileSampled = options.getInternalTracesSampler().sampleContinuousProfile();
    profilingTracesDirPath = options.getProfilingTracesDirPath();
    isProfilingEnabled = options.isProfilingEnabled();
    isContinuousProfilingEnabled = options.isContinuousProfilingEnabled();
    profilingTracesHz = options.getProfilingTracesHz();
  }

  public void setProfileSampled(final boolean profileSampled) {
    this.profileSampled = profileSampled;
  }

  public boolean isProfileSampled() {
    return profileSampled;
  }

  public void setContinuousProfileSampled(boolean continuousProfileSampled) {
    this.continuousProfileSampled = continuousProfileSampled;
  }

  public boolean isContinuousProfileSampled() {
    return continuousProfileSampled;
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

  public void setContinuousProfilingEnabled(final boolean continuousProfilingEnabled) {
    isContinuousProfilingEnabled = continuousProfilingEnabled;
  }

  public boolean isContinuousProfilingEnabled() {
    return isContinuousProfilingEnabled;
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
    public static final String CONTINUOUS_PROFILE_SAMPLED = "continuous_profile_sampled";
    public static final String TRACE_SAMPLED = "trace_sampled";
    public static final String TRACE_SAMPLE_RATE = "trace_sample_rate";
    public static final String PROFILING_TRACES_DIR_PATH = "profiling_traces_dir_path";
    public static final String IS_PROFILING_ENABLED = "is_profiling_enabled";
    public static final String IS_CONTINUOUS_PROFILING_ENABLED = "is_continuous_profiling_enabled";
    public static final String PROFILING_TRACES_HZ = "profiling_traces_hz";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.PROFILE_SAMPLED).value(logger, profileSampled);
    writer.name(JsonKeys.PROFILE_SAMPLE_RATE).value(logger, profileSampleRate);
    writer.name(JsonKeys.CONTINUOUS_PROFILE_SAMPLED).value(logger, continuousProfileSampled);
    writer.name(JsonKeys.TRACE_SAMPLED).value(logger, traceSampled);
    writer.name(JsonKeys.TRACE_SAMPLE_RATE).value(logger, traceSampleRate);
    writer.name(JsonKeys.PROFILING_TRACES_DIR_PATH).value(logger, profilingTracesDirPath);
    writer.name(JsonKeys.IS_PROFILING_ENABLED).value(logger, isProfilingEnabled);
    writer
        .name(JsonKeys.IS_CONTINUOUS_PROFILING_ENABLED)
        .value(logger, isContinuousProfilingEnabled);
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
      implements JsonDeserializer<SentryAppStartProfilingOptions> {

    @Override
    public @NotNull SentryAppStartProfilingOptions deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      SentryAppStartProfilingOptions options = new SentryAppStartProfilingOptions();
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
          case JsonKeys.CONTINUOUS_PROFILE_SAMPLED:
            Boolean continuousProfileSampled = reader.nextBooleanOrNull();
            if (continuousProfileSampled != null) {
              options.continuousProfileSampled = continuousProfileSampled;
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
          case JsonKeys.IS_CONTINUOUS_PROFILING_ENABLED:
            Boolean isContinuousProfilingEnabled = reader.nextBooleanOrNull();
            if (isContinuousProfilingEnabled != null) {
              options.isContinuousProfilingEnabled = isContinuousProfilingEnabled;
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
