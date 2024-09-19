package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Geo implements JsonUnknown, JsonSerializable {

  /** Human readable city name. * */
  @Nullable private String city;

  /** Two-letter country code (ISO 3166-1 alpha-2). * */
  @Nullable private String countryCode;

  /** Human readable region name or code. * */
  @Nullable private String region;

  /** unknown fields, only internal usage. */
  private @Nullable Map<String, @NotNull Object> unknown;

  public Geo() {}

  public Geo(final @NotNull Geo geo) {
    this.city = geo.city;
    this.countryCode = geo.countryCode;
    this.region = geo.region;
  }

  /**
   * Creates geo from a map.
   *
   * @param map - The geo data as map
   * @return the geo
   */
  public static Geo fromMap(@NotNull Map<String, Object> map) {
    final Geo geo = new Geo();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      switch (entry.getKey()) {
        case JsonKeys.CITY:
          geo.city = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.COUNTRY_CODE:
          geo.countryCode = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.REGION:
          geo.region = (value instanceof String) ? (String) value : null;
          break;
        default:
          break;
      }
    }
    return geo;
  }

  /**
   * Gets the human readable city name.
   *
   * @return human readable city name
   */
  public @Nullable String getCity() {
    return city;
  }

  /**
   * Sets the human readable city name.
   *
   * @param city human readable city name
   */
  public void setCity(final @Nullable String city) {
    this.city = city;
  }

  /**
   * Gets the two-letter country code (ISO 3166-1 alpha-2).
   *
   * @return two-letter country code (ISO 3166-1 alpha-2).
   */
  public @Nullable String getCountryCode() {
    return countryCode;
  }

  /**
   * Sets the two-letter country code (ISO 3166-1 alpha-2).
   *
   * @param countryCode two-letter country code (ISO 3166-1 alpha-2).
   */
  public void setCountryCode(final @Nullable String countryCode) {
    this.countryCode = countryCode;
  }

  /**
   * Gets the human readable region name or code.
   *
   * @return human readable region name or code.
   */
  public @Nullable String getRegion() {
    return region;
  }

  /**
   * Sets the human readable region name or code.
   *
   * @param region human readable region name or code.
   */
  public void setRegion(final @Nullable String region) {
    this.region = region;
  }

  // region json

  public static final class JsonKeys {
    public static final String CITY = "city";
    public static final String COUNTRY_CODE = "country_code";
    public static final String REGION = "region";
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

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (city != null) {
      writer.name(JsonKeys.CITY).value(city);
    }
    if (countryCode != null) {
      writer.name(JsonKeys.COUNTRY_CODE).value(countryCode);
    }
    if (region != null) {
      writer.name(JsonKeys.REGION).value(region);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<Geo> {

    @Override
    public @NotNull Geo deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      final Geo geo = new Geo();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.CITY:
            geo.city = reader.nextStringOrNull();
            break;
          case JsonKeys.COUNTRY_CODE:
            geo.countryCode = reader.nextStringOrNull();
            break;
          case JsonKeys.REGION:
            geo.region = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      geo.setUnknown(unknown);
      reader.endObject();
      return geo;
    }
  }

  // endregion json
}
