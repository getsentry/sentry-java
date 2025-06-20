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

public final class Gpu implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "gpu";

  /** The name of the graphics device. */
  private @Nullable String name;

  /** The PCI identifier of the graphics device. */
  private @Nullable Integer id;

  /** The PCI vendor identifier of the graphics device. */
  private @Nullable String vendorId;

  /** The vendor name as reported by the graphics device. */
  private @Nullable String vendorName;

  /** The total GPU memory available in Megabytes. */
  private @Nullable Integer memorySize;

  /**
   * The device low-level API type.
   *
   * <p>Examples: `"Apple Metal"` or `"Direct3D11"`
   */
  private @Nullable String apiType;

  /** Whether the GPU has multi-threaded rendering or not. */
  private @Nullable Boolean multiThreadedRendering;

  /** The Version of the graphics device. */
  private @Nullable String version;

  /** The Non-Power-Of-Two support. */
  private @Nullable String npotSupport;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public Gpu() {}

  Gpu(final @NotNull Gpu gpu) {
    this.name = gpu.name;
    this.id = gpu.id;
    this.vendorId = gpu.vendorId;
    this.vendorName = gpu.vendorName;
    this.memorySize = gpu.memorySize;
    this.apiType = gpu.apiType;
    this.multiThreadedRendering = gpu.multiThreadedRendering;
    this.version = gpu.version;
    this.npotSupport = gpu.npotSupport;
    this.unknown = CollectionUtils.newConcurrentHashMap(gpu.unknown);
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public @Nullable Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public @Nullable String getVendorId() {
    return vendorId;
  }

  public void setVendorId(@Nullable String vendorId) {
    this.vendorId = vendorId;
  }

  public @Nullable String getVendorName() {
    return vendorName;
  }

  public void setVendorName(final @Nullable String vendorName) {
    this.vendorName = vendorName;
  }

  public @Nullable Integer getMemorySize() {
    return memorySize;
  }

  public void setMemorySize(final @Nullable Integer memorySize) {
    this.memorySize = memorySize;
  }

  public @Nullable String getApiType() {
    return apiType;
  }

  public void setApiType(final @Nullable String apiType) {
    this.apiType = apiType;
  }

  public @Nullable Boolean isMultiThreadedRendering() {
    return multiThreadedRendering;
  }

  public void setMultiThreadedRendering(final @Nullable Boolean multiThreadedRendering) {
    this.multiThreadedRendering = multiThreadedRendering;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(final @Nullable String version) {
    this.version = version;
  }

  public @Nullable String getNpotSupport() {
    return npotSupport;
  }

  public void setNpotSupport(final @Nullable String npotSupport) {
    this.npotSupport = npotSupport;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Gpu gpu = (Gpu) o;
    return Objects.equals(name, gpu.name)
        && Objects.equals(id, gpu.id)
        && Objects.equals(vendorId, gpu.vendorId)
        && Objects.equals(vendorName, gpu.vendorName)
        && Objects.equals(memorySize, gpu.memorySize)
        && Objects.equals(apiType, gpu.apiType)
        && Objects.equals(multiThreadedRendering, gpu.multiThreadedRendering)
        && Objects.equals(version, gpu.version)
        && Objects.equals(npotSupport, gpu.npotSupport);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        id,
        vendorId,
        vendorName,
        memorySize,
        apiType,
        multiThreadedRendering,
        version,
        npotSupport);
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String ID = "id";
    public static final String VENDOR_ID = "vendor_id";
    public static final String VENDOR_NAME = "vendor_name";
    public static final String MEMORY_SIZE = "memory_size";
    public static final String API_TYPE = "api_type";
    public static final String MULTI_THREADED_RENDERING = "multi_threaded_rendering";
    public static final String VERSION = "version";
    public static final String NPOT_SUPPORT = "npot_support";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (id != null) {
      writer.name(JsonKeys.ID).value(id);
    }
    if (vendorId != null) {
      writer.name(JsonKeys.VENDOR_ID).value(vendorId);
    }
    if (vendorName != null) {
      writer.name(JsonKeys.VENDOR_NAME).value(vendorName);
    }
    if (memorySize != null) {
      writer.name(JsonKeys.MEMORY_SIZE).value(memorySize);
    }
    if (apiType != null) {
      writer.name(JsonKeys.API_TYPE).value(apiType);
    }
    if (multiThreadedRendering != null) {
      writer.name(JsonKeys.MULTI_THREADED_RENDERING).value(multiThreadedRendering);
    }
    if (version != null) {
      writer.name(JsonKeys.VERSION).value(version);
    }
    if (npotSupport != null) {
      writer.name(JsonKeys.NPOT_SUPPORT).value(npotSupport);
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

  public static final class Deserializer implements JsonDeserializer<Gpu> {
    @Override
    public @NotNull Gpu deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Gpu gpu = new Gpu();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            gpu.name = reader.nextStringOrNull();
            break;
          case JsonKeys.ID:
            gpu.id = reader.nextIntegerOrNull();
            break;
          case JsonKeys.VENDOR_ID:
            gpu.vendorId = reader.nextStringOrNull();
            break;
          case JsonKeys.VENDOR_NAME:
            gpu.vendorName = reader.nextStringOrNull();
            break;
          case JsonKeys.MEMORY_SIZE:
            gpu.memorySize = reader.nextIntegerOrNull();
            break;
          case JsonKeys.API_TYPE:
            gpu.apiType = reader.nextStringOrNull();
            break;
          case JsonKeys.MULTI_THREADED_RENDERING:
            gpu.multiThreadedRendering = reader.nextBooleanOrNull();
            break;
          case JsonKeys.VERSION:
            gpu.version = reader.nextStringOrNull();
            break;
          case JsonKeys.NPOT_SUPPORT:
            gpu.npotSupport = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      gpu.setUnknown(unknown);
      reader.endObject();
      return gpu;
    }
  }
}
