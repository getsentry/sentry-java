package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryOptions;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debugging and processing meta information.
 *
 * <p>The debug meta interface carries debug information for processing errors and crash reports.
 * Sentry amends the information in this interface.
 *
 * <p>Example (look at field types to see more detail):
 *
 * <p>```json { "debug_meta": { "images": [], "sdk_info": { "sdk_name": "iOS", "version_major": 10,
 * "version_minor": 3, "version_patchlevel": 0 } } } ```
 */
public final class DebugMeta implements JsonUnknown, JsonSerializable {
  /** Information about the system SDK (e.g. iOS SDK). */
  private @Nullable SdkInfo sdkInfo;

  /** List of debug information files (debug images). */
  private @Nullable List<DebugImage> images;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable List<DebugImage> getImages() {
    return images;
  }

  public void setImages(final @Nullable List<DebugImage> images) {
    this.images = images != null ? new ArrayList<>(images) : null;
  }

  public @Nullable SdkInfo getSdkInfo() {
    return sdkInfo;
  }

  public void setSdkInfo(final @Nullable SdkInfo sdkInfo) {
    this.sdkInfo = sdkInfo;
  }

  @ApiStatus.Internal
  public static @Nullable DebugMeta buildDebugMeta(
      final @Nullable DebugMeta eventDebugMeta, final @NotNull SentryOptions options) {
    final @NotNull List<DebugImage> debugImages = new ArrayList<>();

    if (options.getProguardUuid() != null) {
      final DebugImage proguardMappingImage = new DebugImage();
      proguardMappingImage.setType(DebugImage.PROGUARD);
      proguardMappingImage.setUuid(options.getProguardUuid());
      debugImages.add(proguardMappingImage);
    }

    for (final @NotNull String bundleId : options.getBundleIds()) {
      final DebugImage sourceBundleImage = new DebugImage();
      sourceBundleImage.setType(DebugImage.JVM);
      sourceBundleImage.setDebugId(bundleId);
      debugImages.add(sourceBundleImage);
    }

    if (!debugImages.isEmpty()) {
      DebugMeta debugMeta = eventDebugMeta;

      if (debugMeta == null) {
        debugMeta = new DebugMeta();
      }
      if (debugMeta.getImages() == null) {
        debugMeta.setImages(debugImages);
      } else {
        debugMeta.getImages().addAll(debugImages);
      }

      return debugMeta;
    }
    return null;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String SDK_INFO = "sdk_info";
    public static final String IMAGES = "images";
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
    if (sdkInfo != null) {
      writer.name(JsonKeys.SDK_INFO).value(logger, sdkInfo);
    }
    if (images != null) {
      writer.name(JsonKeys.IMAGES).value(logger, images);
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

  public static final class Deserializer implements JsonDeserializer<DebugMeta> {
    @Override
    public @NotNull DebugMeta deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      DebugMeta debugMeta = new DebugMeta();
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SDK_INFO:
            debugMeta.sdkInfo = reader.nextOrNull(logger, new SdkInfo.Deserializer());
            break;
          case JsonKeys.IMAGES:
            debugMeta.images = reader.nextListOrNull(logger, new DebugImage.Deserializer());
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

      debugMeta.setUnknown(unknown);
      return debugMeta;
    }
  }
}
