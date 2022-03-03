package io.sentry;

import java.io.File;
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
  private final @NotNull String platform;
  private final @NotNull String build_id;

  // Transaction info
  private final @NotNull String transaction_name;

  // App info
  private final @NotNull String version_name;
  private final @NotNull String version_code;

  // Stacktrace context
  private final @NotNull String transaction_id;
  private final @NotNull String trace_id;
  private final @NotNull String stacktrace_id;
  private final @NotNull String environment;

  // Stacktrace (file)
  /** Stacktrace encoded with Base64 */
  private @Nullable String stacktrace = null;

  public ProfilingTraceData(
      @NotNull File traceFile,
      @NotNull ITransaction transaction,
      int sdkInt,
      @Nullable String deviceManufacturer,
      @Nullable String deviceModel,
      @Nullable String deviceOsVersion,
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
    this.device_os_build_number = "";
    this.device_os_name = "android";
    this.device_os_version = deviceOsVersion != null ? deviceOsVersion : "";
    this.platform = "android";
    this.build_id = buildId != null ? buildId : "";

    // Transaction info
    this.transaction_name = transaction.getName();

    // App info
    this.version_name = versionName != null ? versionName : "";
    this.version_code = versionCode != null ? versionCode : "";

    // Stacktrace context
    this.transaction_id = transaction.getEventId().toString();
    this.trace_id = transaction.getSpanContext().getTraceId().toString();
    this.stacktrace_id = UUID.randomUUID().toString();
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

  public @NotNull String getStacktrace_id() {
    return stacktrace_id;
  }

  public @NotNull String getEnvironment() {
    return environment;
  }

  public @Nullable String getStacktrace() {
    return stacktrace;
  }

  public void setStacktrace(@Nullable String stacktrace) {
    this.stacktrace = stacktrace;
  }
}
