package io.sentry;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfilingTraceData {

  // This field is transient so that it's ignored by Gson
  private final transient @NotNull File traceFile;

  // Device metadata
  private final int android_api_level;
  private final @NotNull String device_locale;
  private final @NotNull String device_manufacturer;
  private final @NotNull String device_model;
  private final @NotNull String device_os_build_number;
  private final @NotNull String device_os_name;
  private final @NotNull String device_os_version;
  private final boolean device_is_emulator;
  private final @NotNull List<Integer> device_cpu_frequencies;
  private final @NotNull String device_physical_memory_bytes;
  private final @NotNull String platform;
  private final @NotNull String build_id;

  // Transaction info
  private final @NotNull String transaction_name;
  // duration_ns is a String to avoid issues with numbers and json
  private final @NotNull String duration_ns;

  // App info
  private final @NotNull String version_name;
  private final @NotNull String version_code;

  // Stacktrace context
  private final @NotNull String transaction_id;
  private final @NotNull String trace_id;
  private final @NotNull String profile_id;
  private final @NotNull String environment;

  // Stacktrace (file)
  /** Profile trace encoded with Base64 */
  private @Nullable String sampled_profile = null;

  public ProfilingTraceData(
      @NotNull File traceFile,
      @NotNull ITransaction transaction,
      @NotNull String durationNanos,
      int sdkInt,
      @Nullable String deviceManufacturer,
      @Nullable String deviceModel,
      @Nullable String deviceOsVersion,
      @Nullable Boolean deviceIsEmulator,
      @NotNull List<Integer> deviceCpuFrequencies,
      @Nullable String devicePhysicalMemoryBytes,
      @Nullable String buildId,
      @Nullable String versionName,
      @Nullable String versionCode,
      @Nullable String environment) {
    this.traceFile = traceFile;

    // Device metadata
    this.android_api_level = sdkInt;
    this.device_locale = Locale.getDefault().toString();
    this.device_manufacturer = deviceManufacturer != null ? deviceManufacturer : "";
    this.device_model = deviceModel != null ? deviceModel : "";
    this.device_os_version = deviceOsVersion != null ? deviceOsVersion : "";
    this.device_is_emulator = deviceIsEmulator != null ? deviceIsEmulator : false;
    this.device_cpu_frequencies = deviceCpuFrequencies;
    this.device_physical_memory_bytes =
        devicePhysicalMemoryBytes != null ? devicePhysicalMemoryBytes : "0";
    this.device_os_build_number = "";
    this.device_os_name = "android";
    this.platform = "android";
    this.build_id = buildId != null ? buildId : "";

    // Transaction info
    this.transaction_name = transaction.getName();
    this.duration_ns = durationNanos;

    // App info
    this.version_name = versionName != null ? versionName : "";
    this.version_code = versionCode != null ? versionCode : "";

    // Stacktrace context
    this.transaction_id = transaction.getEventId().toString();
    this.trace_id = transaction.getSpanContext().getTraceId().toString();
    this.profile_id = UUID.randomUUID().toString();
    this.environment = environment != null ? environment : "";
  }

  public @NotNull File getTraceFile() {
    return traceFile;
  }

  public @NotNull String getTraceId() {
    return trace_id;
  }

  public int getAndroid_api_level() {
    return android_api_level;
  }

  public @NotNull String getDevice_locale() {
    return device_locale;
  }

  public @NotNull String getDevice_manufacturer() {
    return device_manufacturer;
  }

  public @NotNull String getDevice_model() {
    return device_model;
  }

  public @NotNull String getDevice_os_build_number() {
    return device_os_build_number;
  }

  public @NotNull String getDevice_os_name() {
    return device_os_name;
  }

  public @NotNull String getDevice_os_version() {
    return device_os_version;
  }

  public boolean isDevice_is_emulator() {
    return device_is_emulator;
  }

  public @NotNull String getPlatform() {
    return platform;
  }

  public @NotNull String getBuild_id() {
    return build_id;
  }

  public @NotNull String getTransaction_name() {
    return transaction_name;
  }

  public @NotNull String getVersion_name() {
    return version_name;
  }

  public @NotNull String getVersion_code() {
    return version_code;
  }

  public @NotNull String getTransaction_id() {
    return transaction_id;
  }

  public @NotNull String getTrace_id() {
    return trace_id;
  }

  public @NotNull String getProfile_id() {
    return profile_id;
  }

  public @NotNull String getEnvironment() {
    return environment;
  }

  public @Nullable String getSampled_profile() {
    return sampled_profile;
  }

  public @NotNull String getDuration_ns() {
    return duration_ns;
  }

  public @NotNull List<Integer> getDevice_cpu_frequencies() {
    return device_cpu_frequencies;
  }

  public @NotNull String getDevice_physical_memory_bytes() {
    return device_physical_memory_bytes;
  }

  public void setSampled_profile(@Nullable String sampledProfile) {
    this.sampled_profile = sampledProfile;
  }
}
