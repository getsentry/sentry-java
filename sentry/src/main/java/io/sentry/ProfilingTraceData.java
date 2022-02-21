package io.sentry;

import java.io.File;
import java.util.Locale;
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

  // Transaction info
  private final @NotNull String error_code;
  private final @NotNull String error_description;
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
      @NotNull String deviceManufacturer,
      @NotNull String deviceModel,
      @NotNull String deviceOsBuildNumber,
      @NotNull String deviceOsName,
      @NotNull String deviceOsVersion,
      @NotNull String versionName,
      @NotNull String versionCode,
      @Nullable String environment) {
    this.traceFile = traceFile;

    // Device metadata
    this.android_api_level = sdkInt;
    this.device_locale = Locale.getDefault().toString();
    this.device_manufacturer = deviceManufacturer;
    this.device_model = deviceModel;
    this.device_os_build_number = deviceOsBuildNumber;
    this.device_os_name = deviceOsName;
    this.device_os_version = deviceOsVersion;
    this.platform = "android";

    // Transaction info
    this.error_code = transaction.getOperation();
    this.error_description =
        transaction.getDescription() != null ? transaction.getDescription() : "";
    this.transaction_name = transaction.getName();

    // App info
    this.version_name = versionName;
    this.version_code = versionCode;

    // Stacktrace context
    this.transaction_id = transaction.getEventId().toString();
    this.trace_id = transaction.getSpanContext().getTraceId().toString();
    this.stacktrace_id = transaction.getSpanContext().getSpanId().toString();
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

  public @NotNull String getError_code() {
    return error_code;
  }

  public @NotNull String getError_description() {
    return error_description;
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
