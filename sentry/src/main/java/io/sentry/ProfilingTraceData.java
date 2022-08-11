package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfilingTraceData implements JsonUnknown, JsonSerializable {

  /**
   * Default value for {@link SentryEvent#getEnvironment()} set when both event and {@link
   * SentryOptions} do not have the environment field set.
   */
  private static final String DEFAULT_ENVIRONMENT = "production";

  private @NotNull File traceFile;
  private @Nullable Callable<List<Integer>> deviceCpuFrequenciesReader;

  // Device metadata
  private int androidApiLevel;
  private @NotNull String deviceLocale;
  private @NotNull String deviceManufacturer;
  private @NotNull String deviceModel;
  private @NotNull String deviceOsBuildNumber;
  private @NotNull String deviceOsName;
  private @NotNull String deviceOsVersion;
  private boolean deviceIsEmulator;
  private @NotNull String cpuArchitecture;
  private @NotNull List<Integer> deviceCpuFrequencies = new ArrayList<>();
  private @NotNull String devicePhysicalMemoryBytes;
  private @NotNull String platform;
  private @NotNull String buildId;

  // Transaction info
  private @NotNull List<ProfilingTransactionData> transactions;
  private @NotNull String transactionName;
  // duration_ns is a String to avoid issues with numbers and json
  private @NotNull String durationNs;

  // App info
  private @NotNull String versionName;
  private @NotNull String versionCode;

  // Stacktrace context
  private @NotNull String transactionId;
  private @NotNull String traceId;
  private @NotNull String profileId;
  private @NotNull String environment;

  // Stacktrace (file)
  /** Profile trace encoded with Base64 */
  private @Nullable String sampledProfile = null;

  private ProfilingTraceData() {
    this(new File("dummy"), NoOpTransaction.getInstance());
  }

  public ProfilingTraceData(@NotNull File traceFile, @NotNull ITransaction transaction) {
    this(
        traceFile,
        new ArrayList<>(),
        transaction,
        "0",
        0,
        "",
        // Don't use method reference. This can cause issues on Android
        () -> new ArrayList<>(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public ProfilingTraceData(
      @NotNull File traceFile,
      @NotNull List<ProfilingTransactionData> transactions,
      @NotNull ITransaction transaction,
      @NotNull String durationNanos,
      int sdkInt,
      @NotNull String cpuArchitecture,
      @NotNull Callable<List<Integer>> deviceCpuFrequenciesReader,
      @Nullable String deviceManufacturer,
      @Nullable String deviceModel,
      @Nullable String deviceOsVersion,
      @Nullable Boolean deviceIsEmulator,
      @Nullable String devicePhysicalMemoryBytes,
      @Nullable String buildId,
      @Nullable String versionName,
      @Nullable String versionCode,
      @Nullable String environment) {
    this.traceFile = traceFile;
    this.cpuArchitecture = cpuArchitecture;
    this.deviceCpuFrequenciesReader = deviceCpuFrequenciesReader;

    // Device metadata
    this.androidApiLevel = sdkInt;
    this.deviceLocale = Locale.getDefault().toString();
    this.deviceManufacturer = deviceManufacturer != null ? deviceManufacturer : "";
    this.deviceModel = deviceModel != null ? deviceModel : "";
    this.deviceOsVersion = deviceOsVersion != null ? deviceOsVersion : "";
    this.deviceIsEmulator = deviceIsEmulator != null ? deviceIsEmulator : false;
    this.devicePhysicalMemoryBytes =
        devicePhysicalMemoryBytes != null ? devicePhysicalMemoryBytes : "0";
    this.deviceOsBuildNumber = "";
    this.deviceOsName = "android";
    this.platform = "android";
    this.buildId = buildId != null ? buildId : "";

    // Transaction info
    this.transactions = transactions;
    this.transactionName = transaction.getName();
    this.durationNs = durationNanos;

    // App info
    this.versionName = versionName != null ? versionName : "";
    this.versionCode = versionCode != null ? versionCode : "";

    // Stacktrace context
    this.transactionId = transaction.getEventId().toString();
    this.traceId = transaction.getSpanContext().getTraceId().toString();
    this.profileId = UUID.randomUUID().toString();
    this.environment = environment != null ? environment : DEFAULT_ENVIRONMENT;
  }

  private @Nullable Map<String, Object> unknown;

  public @NotNull File getTraceFile() {
    return traceFile;
  }

  public int getAndroidApiLevel() {
    return androidApiLevel;
  }

  public @NotNull String getCpuArchitecture() {
    return cpuArchitecture;
  }

  public @NotNull String getDeviceLocale() {
    return deviceLocale;
  }

  public @NotNull String getDeviceManufacturer() {
    return deviceManufacturer;
  }

  public @NotNull String getDeviceModel() {
    return deviceModel;
  }

  public @NotNull String getDeviceOsBuildNumber() {
    return deviceOsBuildNumber;
  }

  public @NotNull String getDeviceOsName() {
    return deviceOsName;
  }

  public @NotNull String getDeviceOsVersion() {
    return deviceOsVersion;
  }

  public boolean isDeviceIsEmulator() {
    return deviceIsEmulator;
  }

  public @NotNull String getPlatform() {
    return platform;
  }

  public @NotNull String getBuildId() {
    return buildId;
  }

  public @NotNull String getTransactionName() {
    return transactionName;
  }

  public @NotNull String getVersionName() {
    return versionName;
  }

  public @NotNull String getVersionCode() {
    return versionCode;
  }

  public @NotNull String getTransactionId() {
    return transactionId;
  }

  public @NotNull List<ProfilingTransactionData> getTransactions() {
    return transactions;
  }

  public @NotNull String getTraceId() {
    return traceId;
  }

  public @NotNull String getProfileId() {
    return profileId;
  }

  public @NotNull String getEnvironment() {
    return environment;
  }

  public @Nullable String getSampledProfile() {
    return sampledProfile;
  }

  public @NotNull String getDurationNs() {
    return durationNs;
  }

  public @NotNull List<Integer> getDeviceCpuFrequencies() {
    return deviceCpuFrequencies;
  }

  public @NotNull String getDevicePhysicalMemoryBytes() {
    return devicePhysicalMemoryBytes;
  }

  public void setAndroidApiLevel(int androidApiLevel) {
    this.androidApiLevel = androidApiLevel;
  }

  public void setCpuArchitecture(@NotNull String cpuArchitecture) {
    this.cpuArchitecture = cpuArchitecture;
  }

  public void setDeviceLocale(@NotNull String deviceLocale) {
    this.deviceLocale = deviceLocale;
  }

  public void setDeviceManufacturer(@NotNull String deviceManufacturer) {
    this.deviceManufacturer = deviceManufacturer;
  }

  public void setDeviceModel(@NotNull String deviceModel) {
    this.deviceModel = deviceModel;
  }

  public void setDeviceOsBuildNumber(@NotNull String deviceOsBuildNumber) {
    this.deviceOsBuildNumber = deviceOsBuildNumber;
  }

  public void setDeviceOsVersion(@NotNull String deviceOsVersion) {
    this.deviceOsVersion = deviceOsVersion;
  }

  public void setDeviceIsEmulator(boolean deviceIsEmulator) {
    this.deviceIsEmulator = deviceIsEmulator;
  }

  public void setDeviceCpuFrequencies(@NotNull List<Integer> deviceCpuFrequencies) {
    this.deviceCpuFrequencies = deviceCpuFrequencies;
  }

  public void setDevicePhysicalMemoryBytes(@NotNull String devicePhysicalMemoryBytes) {
    this.devicePhysicalMemoryBytes = devicePhysicalMemoryBytes;
  }

  public void setBuildId(@NotNull String buildId) {
    this.buildId = buildId;
  }

  public void setTransactionName(@NotNull String transactionName) {
    this.transactionName = transactionName;
  }

  public void setDurationNs(@NotNull String durationNs) {
    this.durationNs = durationNs;
  }

  public void setVersionName(@NotNull String versionName) {
    this.versionName = versionName;
  }

  public void setVersionCode(@NotNull String versionCode) {
    this.versionCode = versionCode;
  }

  public void setTransactionId(@NotNull String transactionId) {
    this.transactionId = transactionId;
  }

  public void setTraceId(@NotNull String traceId) {
    this.traceId = traceId;
  }

  public void setProfileId(@NotNull String profileId) {
    this.profileId = profileId;
  }

  public void setEnvironment(@NotNull String environment) {
    this.environment = environment;
  }

  public void setSampledProfile(@Nullable String sampledProfile) {
    this.sampledProfile = sampledProfile;
  }

  public void readDeviceCpuFrequencies() {
    try {
      if (deviceCpuFrequenciesReader != null) {
        this.deviceCpuFrequencies = deviceCpuFrequenciesReader.call();
      }
    } catch (Throwable ignored) {
      // should never happen
    }
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String ANDROID_API_LEVEL = "android_api_level";
    public static final String DEVICE_LOCALE = "device_locale";
    public static final String DEVICE_MANUFACTURER = "device_manufacturer";
    public static final String DEVICE_MODEL = "device_model";
    public static final String DEVICE_OS_BUILD_NUMBER = "device_os_build_number";
    public static final String DEVICE_OS_NAME = "device_os_name";
    public static final String DEVICE_OS_VERSION = "device_os_version";
    public static final String DEVICE_IS_EMULATOR = "device_is_emulator";
    public static final String ARCHITECTURE = "architecture";
    public static final String DEVICE_CPU_FREQUENCIES = "device_cpu_frequencies";
    public static final String DEVICE_PHYSICAL_MEMORY_BYTES = "device_physical_memory_bytes";
    public static final String PLATFORM = "platform";
    public static final String BUILD_ID = "build_id";
    public static final String TRANSACTION_NAME = "transaction_name";
    public static final String DURATION_NS = "duration_ns";
    public static final String VERSION_NAME = "version_name";
    public static final String VERSION_CODE = "version_code";
    public static final String TRANSACTION_LIST = "transactions";
    public static final String TRANSACTION_ID = "transaction_id";
    public static final String TRACE_ID = "trace_id";
    public static final String PROFILE_ID = "profile_id";
    public static final String ENVIRONMENT = "environment";
    public static final String SAMPLED_PROFILE = "sampled_profile";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.ANDROID_API_LEVEL).value(logger, androidApiLevel);
    writer.name(JsonKeys.DEVICE_LOCALE).value(logger, deviceLocale);
    writer.name(JsonKeys.DEVICE_MANUFACTURER).value(deviceManufacturer);
    writer.name(JsonKeys.DEVICE_MODEL).value(deviceModel);
    writer.name(JsonKeys.DEVICE_OS_BUILD_NUMBER).value(deviceOsBuildNumber);
    writer.name(JsonKeys.DEVICE_OS_NAME).value(deviceOsName);
    writer.name(JsonKeys.DEVICE_OS_VERSION).value(deviceOsVersion);
    writer.name(JsonKeys.DEVICE_IS_EMULATOR).value(deviceIsEmulator);
    writer.name(JsonKeys.ARCHITECTURE).value(logger, cpuArchitecture);
    // Backend expects the list of frequencies, even if empty
    writer.name(JsonKeys.DEVICE_CPU_FREQUENCIES).value(logger, deviceCpuFrequencies);
    writer.name(JsonKeys.DEVICE_PHYSICAL_MEMORY_BYTES).value(devicePhysicalMemoryBytes);
    writer.name(JsonKeys.PLATFORM).value(platform);
    writer.name(JsonKeys.BUILD_ID).value(buildId);
    writer.name(JsonKeys.TRANSACTION_NAME).value(transactionName);
    writer.name(JsonKeys.DURATION_NS).value(durationNs);
    writer.name(JsonKeys.VERSION_NAME).value(versionName);
    writer.name(JsonKeys.VERSION_CODE).value(versionCode);
    if (!transactions.isEmpty()) {
      writer.name(JsonKeys.TRANSACTION_LIST).value(logger, transactions);
    }
    writer.name(JsonKeys.TRANSACTION_ID).value(transactionId);
    writer.name(JsonKeys.TRACE_ID).value(traceId);
    writer.name(JsonKeys.PROFILE_ID).value(profileId);
    writer.name(JsonKeys.ENVIRONMENT).value(environment);
    if (sampledProfile != null) {
      writer.name(JsonKeys.SAMPLED_PROFILE).value(sampledProfile);
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

  public static final class Deserializer implements JsonDeserializer<ProfilingTraceData> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull ProfilingTraceData deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ProfilingTraceData data = new ProfilingTraceData();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ANDROID_API_LEVEL:
            Integer apiLevel = reader.nextIntegerOrNull();
            if (apiLevel != null) {
              data.androidApiLevel = apiLevel;
            }
            break;
          case JsonKeys.DEVICE_LOCALE:
            String deviceLocale = reader.nextStringOrNull();
            if (deviceLocale != null) {
              data.deviceLocale = deviceLocale;
            }
            break;
          case JsonKeys.DEVICE_MANUFACTURER:
            String deviceManufacturer = reader.nextStringOrNull();
            if (deviceManufacturer != null) {
              data.deviceManufacturer = deviceManufacturer;
            }
            break;
          case JsonKeys.DEVICE_MODEL:
            String deviceModel = reader.nextStringOrNull();
            if (deviceModel != null) {
              data.deviceModel = deviceModel;
            }
            break;
          case JsonKeys.DEVICE_OS_BUILD_NUMBER:
            String deviceOsBuildNumber = reader.nextStringOrNull();
            if (deviceOsBuildNumber != null) {
              data.deviceOsBuildNumber = deviceOsBuildNumber;
            }
            break;
          case JsonKeys.DEVICE_OS_NAME:
            String deviceOsName = reader.nextStringOrNull();
            if (deviceOsName != null) {
              data.deviceOsName = deviceOsName;
            }
            break;
          case JsonKeys.DEVICE_OS_VERSION:
            String deviceOsVersion = reader.nextStringOrNull();
            if (deviceOsVersion != null) {
              data.deviceOsVersion = deviceOsVersion;
            }
            break;
          case JsonKeys.DEVICE_IS_EMULATOR:
            Boolean deviceIsEmulator = reader.nextBooleanOrNull();
            if (deviceIsEmulator != null) {
              data.deviceIsEmulator = deviceIsEmulator;
            }
            break;
          case JsonKeys.ARCHITECTURE:
            String cpuArchitecture = reader.nextStringOrNull();
            if (cpuArchitecture != null) {
              data.cpuArchitecture = cpuArchitecture;
            }
            break;
          case JsonKeys.DEVICE_CPU_FREQUENCIES:
            List<Integer> deviceCpuFrequencies = (List<Integer>) reader.nextObjectOrNull();
            if (deviceCpuFrequencies != null) {
              data.deviceCpuFrequencies = deviceCpuFrequencies;
            }
            break;
          case JsonKeys.DEVICE_PHYSICAL_MEMORY_BYTES:
            String devicePhysicalMemoryBytes = reader.nextStringOrNull();
            if (devicePhysicalMemoryBytes != null) {
              data.devicePhysicalMemoryBytes = devicePhysicalMemoryBytes;
            }
            break;
          case JsonKeys.PLATFORM:
            String platform = reader.nextStringOrNull();
            if (platform != null) {
              data.platform = platform;
            }
            break;
          case JsonKeys.BUILD_ID:
            String buildId = reader.nextStringOrNull();
            if (buildId != null) {
              data.buildId = buildId;
            }
            break;
          case JsonKeys.TRANSACTION_NAME:
            String transactionName = reader.nextStringOrNull();
            if (transactionName != null) {
              data.transactionName = transactionName;
            }
            break;
          case JsonKeys.DURATION_NS:
            String durationNs = reader.nextStringOrNull();
            if (durationNs != null) {
              data.durationNs = durationNs;
            }
            break;
          case JsonKeys.VERSION_NAME:
            String versionName = reader.nextStringOrNull();
            if (versionName != null) {
              data.versionName = versionName;
            }
            break;
          case JsonKeys.VERSION_CODE:
            String versionCode = reader.nextStringOrNull();
            if (versionCode != null) {
              data.versionCode = versionCode;
            }
            break;
          case JsonKeys.TRANSACTION_LIST:
            List<ProfilingTransactionData> transactions =
                reader.nextList(logger, new ProfilingTransactionData.Deserializer());
            if (transactions != null) {
              data.transactions.addAll(transactions);
            }
            break;
          case JsonKeys.TRANSACTION_ID:
            String transactionId = reader.nextStringOrNull();
            if (transactionId != null) {
              data.transactionId = transactionId;
            }
            break;
          case JsonKeys.TRACE_ID:
            String traceId = reader.nextStringOrNull();
            if (traceId != null) {
              data.traceId = traceId;
            }
            break;
          case JsonKeys.PROFILE_ID:
            String profileId = reader.nextStringOrNull();
            if (profileId != null) {
              data.profileId = profileId;
            }
            break;
          case JsonKeys.ENVIRONMENT:
            String environment = reader.nextStringOrNull();
            if (environment != null) {
              data.environment = environment;
            }
            break;
          case JsonKeys.SAMPLED_PROFILE:
            String sampledProfile = reader.nextStringOrNull();
            if (sampledProfile != null) {
              data.sampledProfile = sampledProfile;
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
