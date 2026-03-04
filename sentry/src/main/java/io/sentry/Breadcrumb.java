package io.sentry;

import io.sentry.util.CollectionUtils;
import io.sentry.util.HttpUtils;
import io.sentry.util.Objects;
import io.sentry.util.UrlUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Series of application events */
public final class Breadcrumb implements JsonUnknown, JsonSerializable, Comparable<Breadcrumb> {

  /** A timestamp representing when the breadcrumb occurred in milliseconds. */
  private @Nullable final Long timestampMs;

  /** A timestamp representing when the breadcrumb occurred as java.util.Date. */
  private @Nullable Date timestamp;

  private final @NotNull Long nanos;

  /** If a message is provided, its rendered as text and the whitespace is preserved. */
  private @Nullable String message;

  /** The type of breadcrumb. */
  private @Nullable String type;

  /** Data associated with this breadcrumb. */
  private @NotNull Map<String, @NotNull Object> data = new ConcurrentHashMap<>();

  /** Dotted strings that indicate what the crumb is or where it comes from. */
  private @Nullable String category;

  /**
   * Origin of the breadcrumb that is used to identify source of the breadcrumb. For example hybrid
   * SDKs can identify native breadcrumbs from JS or Flutter.
   */
  private @Nullable String origin;

  /** The level of the event. */
  private @Nullable SentryLevel level;

  /** the unknown fields of breadcrumbs, internal usage only */
  private @Nullable Map<String, Object> unknown;

  /**
   * Breadcrumb ctor
   *
   * @param timestamp the timestamp
   */
  @SuppressWarnings("JavaUtilDate")
  public Breadcrumb(final @NotNull Date timestamp) {
    this.nanos = System.nanoTime();
    this.timestamp = timestamp;
    this.timestampMs = null;
  }

  public Breadcrumb(final long timestamp) {
    this.nanos = System.nanoTime();
    this.timestampMs = timestamp;
    this.timestamp = null;
  }

  Breadcrumb(final @NotNull Breadcrumb breadcrumb) {
    this.nanos = System.nanoTime();
    this.timestamp = breadcrumb.timestamp;
    this.timestampMs = breadcrumb.timestampMs;
    this.message = breadcrumb.message;
    this.type = breadcrumb.type;
    this.category = breadcrumb.category;
    this.origin = breadcrumb.origin;
    final Map<String, Object> dataClone = CollectionUtils.newConcurrentHashMap(breadcrumb.data);
    if (dataClone != null) {
      this.data = dataClone;
    }
    this.unknown = CollectionUtils.newConcurrentHashMap(breadcrumb.unknown);
    this.level = breadcrumb.level;
  }

  /**
   * Creates breadcrumb from a map.
   *
   * @param map - The breadcrumb data as map
   * @param options - the sentry options
   * @return the breadcrumb
   */
  @SuppressWarnings("unchecked")
  public static Breadcrumb fromMap(
      @NotNull Map<String, Object> map, @NotNull SentryOptions options) {

    @NotNull Date timestamp = DateUtils.getCurrentDateTime();
    String message = null;
    String type = null;
    @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
    String category = null;
    String origin = null;
    SentryLevel level = null;
    Map<String, Object> unknown = null;

    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      switch (entry.getKey()) {
        case JsonKeys.TIMESTAMP:
          if (value instanceof String) {
            Date deserializedDate = ObjectReader.dateOrNull((String) value, options.getLogger());
            if (deserializedDate != null) {
              timestamp = deserializedDate;
            }
          }
          break;
        case JsonKeys.MESSAGE:
          message = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.TYPE:
          type = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.DATA:
          final Map<Object, Object> untypedData =
              (value instanceof Map) ? (Map<Object, Object>) value : null;
          if (untypedData != null) {
            for (Map.Entry<Object, Object> dataEntry : untypedData.entrySet()) {
              if (dataEntry.getKey() instanceof String && dataEntry.getValue() != null) {
                data.put((String) dataEntry.getKey(), dataEntry.getValue());
              } else {
                options
                    .getLogger()
                    .log(SentryLevel.WARNING, "Invalid key or null value in data map.");
              }
            }
          }
          break;
        case JsonKeys.CATEGORY:
          category = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.ORIGIN:
          origin = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.LEVEL:
          String levelString = (value instanceof String) ? (String) value : null;
          if (levelString != null) {
            try {
              level = SentryLevel.valueOf(levelString.toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
              // Stub
            }
          }
          break;
        default:
          if (unknown == null) {
            unknown = new ConcurrentHashMap<>();
          }
          unknown.put(entry.getKey(), entry.getValue());
          break;
      }
    }

    final Breadcrumb breadcrumb = new Breadcrumb(timestamp);
    breadcrumb.message = message;
    breadcrumb.type = type;
    breadcrumb.data = data;
    breadcrumb.category = category;
    breadcrumb.origin = origin;
    breadcrumb.level = level;

    breadcrumb.setUnknown(unknown);
    return breadcrumb;
  }

  /**
   * Creates HTTP breadcrumb.
   *
   * @param url - the request URL
   * @param method - the request method
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb http(final @NotNull String url, final @NotNull String method) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    final @NotNull UrlUtils.UrlDetails urlDetails = UrlUtils.parse(url);
    breadcrumb.setType("http");
    breadcrumb.setCategory("http");
    if (urlDetails.getUrl() != null) {
      breadcrumb.setData("url", urlDetails.getUrl());
    }
    breadcrumb.setData("method", method.toUpperCase(Locale.ROOT));
    if (urlDetails.getQuery() != null) {
      breadcrumb.setData("http.query", urlDetails.getQuery());
    }
    if (urlDetails.getFragment() != null) {
      breadcrumb.setData("http.fragment", urlDetails.getFragment());
    }
    return breadcrumb;
  }

  /**
   * Creates HTTP breadcrumb.
   *
   * @param url - the request URL
   * @param method - the request method
   * @param code - the code result. Code can be null when http request did not finish or ended with
   *     network error
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb http(
      final @NotNull String url, final @NotNull String method, final @Nullable Integer code) {
    final Breadcrumb breadcrumb = http(url, method);
    if (code != null) {
      breadcrumb.setData("status_code", code);
      breadcrumb.setLevel(levelFromHttpStatusCode(code));
    }
    return breadcrumb;
  }

  /**
   * Creates a breadcrumb for a GraphQL operation.
   *
   * @param operationName - the name of the GraphQL operation
   * @param operationType - the type of GraphQL operation (e.g. query, mutation, subscription)
   * @param operationId - the ID of the GraphQL operation
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb graphqlOperation(
      final @Nullable String operationName,
      final @Nullable String operationType,
      final @Nullable String operationId) {
    final Breadcrumb breadcrumb = new Breadcrumb();

    breadcrumb.setType("graphql");

    if (operationName != null) {
      breadcrumb.setData("operation_name", operationName);
    }
    if (operationType != null) {
      breadcrumb.setData("operation_type", operationType);
      breadcrumb.setCategory(operationType);
    } else {
      breadcrumb.setCategory("graphql.operation");
    }
    if (operationId != null) {
      breadcrumb.setData("operation_id", operationId);
    }

    return breadcrumb;
  }

  /**
   * Creates a breadcrumb for a GraphQL data fetcher.
   *
   * @param path - the name of the GraphQL operation
   * @param field - the field being fetched
   * @param type - the type being fetched
   * @param objectType - the object type of the GraphQL data fetch operation
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb graphqlDataFetcher(
      final @Nullable String path,
      final @Nullable String field,
      final @Nullable String type,
      final @Nullable String objectType) {
    final Breadcrumb breadcrumb = new Breadcrumb();

    breadcrumb.setType("graphql");
    breadcrumb.setCategory("graphql.fetcher");

    if (path != null) {
      breadcrumb.setData("path", path);
    }
    if (field != null) {
      breadcrumb.setData("field", field);
    }
    if (type != null) {
      breadcrumb.setData("type", type);
    }
    if (objectType != null) {
      breadcrumb.setData("object_type", objectType);
    }

    return breadcrumb;
  }

  /**
   * Creates a breadcrumb for a GraphQL data loader.
   *
   * @param keys - keys to be fetched by the data loader
   * @param keyType - class of the data loaders key(s)
   * @param valueType - class of the data loaders value(s)
   * @param name - name of the data loader
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb graphqlDataLoader(
      final @NotNull Iterable<?> keys,
      final @Nullable Class<?> keyType,
      final @Nullable Class<?> valueType,
      final @Nullable String name) {
    final Breadcrumb breadcrumb = new Breadcrumb();

    breadcrumb.setType("graphql");
    breadcrumb.setCategory("graphql.data_loader");

    final List<String> serializedKeys = new ArrayList<>();
    for (Object key : keys) {
      serializedKeys.add(key.toString());
    }
    breadcrumb.setData("keys", serializedKeys);

    if (keyType != null) {
      breadcrumb.setData("key_type", keyType.getName());
    }

    if (valueType != null) {
      breadcrumb.setData("value_type", valueType.getName());
    }

    if (name != null) {
      breadcrumb.setData("name", name);
    }

    return breadcrumb;
  }

  /**
   * Creates navigation breadcrumb - a navigation event can be a URL change in a web application, or
   * a UI transition in a mobile or desktop application, etc.
   *
   * @param from - the original application state / location
   * @param to - the new application state / location
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb navigation(
      final @NotNull String from, final @NotNull String to) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setCategory("navigation");
    breadcrumb.setType("navigation");
    breadcrumb.setData("from", from);
    breadcrumb.setData("to", to);
    return breadcrumb;
  }

  /**
   * Creates transaction breadcrumb - describing a tracing event.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb transaction(final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("default");
    breadcrumb.setCategory("sentry.transaction");
    breadcrumb.setMessage(message);
    return breadcrumb;
  }

  /**
   * Creates debug breadcrumb - typically a log message. The data part is entirely undefined and as
   * such, completely rendered as a key/value table.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb debug(final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("debug");
    breadcrumb.setMessage(message);
    breadcrumb.setLevel(SentryLevel.DEBUG);
    return breadcrumb;
  }

  /**
   * Creates error breadcrumb.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb error(final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("error");
    breadcrumb.setMessage(message);
    breadcrumb.setLevel(SentryLevel.ERROR);
    return breadcrumb;
  }

  /**
   * Creates info breadcrumb - information that helps identify the root cause of the issue or for
   * whom the error occurred.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb info(final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("info");
    breadcrumb.setMessage(message);
    breadcrumb.setLevel(SentryLevel.INFO);
    return breadcrumb;
  }

  /**
   * Creates query breadcrumb - representing a query that was made in your application.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb query(final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("query");
    breadcrumb.setMessage(message);
    return breadcrumb;
  }

  /**
   * Creates ui breadcrumb - a user interaction with your app's UI.
   *
   * @param category - the category, for example "click"
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb ui(
      final @NotNull String category, final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("default");
    breadcrumb.setCategory("ui." + category);
    breadcrumb.setMessage(message);
    return breadcrumb;
  }

  /**
   * Creates user breadcrumb - a user interaction with your app's UI.
   *
   * @param message - the message
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb user(
      final @NotNull String category, final @NotNull String message) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("user");
    breadcrumb.setCategory(category);
    breadcrumb.setMessage(message);
    return breadcrumb;
  }

  /**
   * Creates user breadcrumb - a user interaction with your app's UI. The breadcrumb can contain
   * additional data like {@code viewId} or {@code viewClass}. By default, the breadcrumb is
   * captured with {@link SentryLevel} INFO level.
   *
   * @param subCategory - the category, for example "click"
   * @param viewId - the human-readable view id, for example "button_load"
   * @param viewClass - the fully qualified class name, for example "android.widget.Button"
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb userInteraction(
      final @NotNull String subCategory,
      final @Nullable String viewId,
      final @Nullable String viewClass) {
    return userInteraction(subCategory, viewId, viewClass, Collections.emptyMap());
  }

  /**
   * Creates user breadcrumb - a user interaction with your app's UI. The breadcrumb can contain
   * additional data like {@code viewId} or {@code viewClass}. By default, the breadcrumb is
   * captured with {@link SentryLevel} INFO level.
   *
   * @param subCategory - the category, for example "click"
   * @param viewId - the human-readable view id, for example "button_load"
   * @param viewClass - the fully qualified class name, for example "android.widget.Button"
   * @param viewTag - the custom tag of the view, for example "button_launch_rocket"
   * @param additionalData - additional properties to be put into the data bag
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb userInteraction(
      final @NotNull String subCategory,
      final @Nullable String viewId,
      final @Nullable String viewClass,
      final @Nullable String viewTag,
      final @NotNull Map<String, Object> additionalData) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("user");
    breadcrumb.setCategory("ui." + subCategory);
    if (viewId != null) {
      breadcrumb.setData("view.id", viewId);
    }
    if (viewClass != null) {
      breadcrumb.setData("view.class", viewClass);
    }
    if (viewTag != null) {
      breadcrumb.setData("view.tag", viewTag);
    }
    for (final Map.Entry<String, Object> entry : additionalData.entrySet()) {
      breadcrumb.getData().put(entry.getKey(), entry.getValue());
    }
    breadcrumb.setLevel(SentryLevel.INFO);
    return breadcrumb;
  }

  /**
   * Creates user breadcrumb - a user interaction with your app's UI. The breadcrumb can contain
   * additional data like {@code viewId} or {@code viewClass}. By default, the breadcrumb is
   * captured with {@link SentryLevel} INFO level.
   *
   * @param subCategory - the category, for example "click"
   * @param viewId - the human-readable view id, for example "button_load"
   * @param viewClass - the fully qualified class name, for example "android.widget.Button"
   * @param additionalData - additional properties to be put into the data bag
   * @return the breadcrumb
   */
  public static @NotNull Breadcrumb userInteraction(
      final @NotNull String subCategory,
      final @Nullable String viewId,
      final @Nullable String viewClass,
      final @NotNull Map<String, Object> additionalData) {

    return userInteraction(subCategory, viewId, viewClass, null, additionalData);
  }

  private static @Nullable SentryLevel levelFromHttpStatusCode(final @NotNull Integer code) {
    if (HttpUtils.isHttpClientError(code)) {
      return SentryLevel.WARNING;
    } else if (HttpUtils.isHttpServerError(code)) {
      return SentryLevel.ERROR;
    } else {
      return null;
    }
  }

  /** Breadcrumb ctor */
  public Breadcrumb() {
    this(System.currentTimeMillis());
  }

  /**
   * Breadcrumb ctor
   *
   * @param message the message
   */
  public Breadcrumb(@Nullable String message) {
    this();
    this.message = message;
  }

  /**
   * Returns the Breadcrumb's timestamp as java.util.Date
   *
   * @return the timestamp
   */
  @SuppressWarnings("JavaUtilDate")
  public @NotNull Date getTimestamp() {
    if (timestamp != null) {
      return (Date) timestamp.clone();
    } else if (timestampMs != null) {
      // we memoize it here into timestamp to avoid instantiating Calendar again and again
      timestamp = DateUtils.getDateTime(timestampMs);
      return timestamp;
    }
    throw new IllegalStateException("No timestamp set for breadcrumb");
  }

  /**
   * Returns the message
   *
   * @return the message
   */
  public @Nullable String getMessage() {
    return message;
  }

  /**
   * Sets the message
   *
   * @param message the message
   */
  public void setMessage(@Nullable String message) {
    this.message = message;
  }

  /**
   * Returns the type
   *
   * @return the type
   */
  public @Nullable String getType() {
    return type;
  }

  /**
   * Sets the type
   *
   * @param type the type
   */
  public void setType(@Nullable String type) {
    this.type = type;
  }

  /**
   * Returns the data map
   *
   * @return the data map
   */
  @ApiStatus.Internal
  @NotNull
  public Map<String, Object> getData() {
    return data;
  }

  /**
   * Returns the value of data[key] or null
   *
   * @param key the key
   * @return the value or null
   */
  @Nullable
  public Object getData(final @Nullable String key) {
    if (key == null) {
      return null;
    }
    return data.get(key);
  }

  /**
   * Sets an entry to the data's map
   *
   * @param key the key
   * @param value the value
   */
  public void setData(@Nullable String key, @Nullable Object value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      removeData(key);
    } else {
      data.put(key, value);
    }
  }

  /**
   * Removes an entry from the data's map
   *
   * @param key the key
   */
  public void removeData(@Nullable String key) {
    if (key == null) {
      return;
    }
    data.remove(key);
  }

  /**
   * Returns the category
   *
   * @return the category
   */
  public @Nullable String getCategory() {
    return category;
  }

  /**
   * Sets the category
   *
   * @param category the category
   */
  public void setCategory(@Nullable String category) {
    this.category = category;
  }

  /**
   * Returns the origin
   *
   * @return the origin
   */
  public @Nullable String getOrigin() {
    return origin;
  }

  /**
   * Sets the origin
   *
   * @param origin the origin
   */
  public void setOrigin(@Nullable String origin) {
    this.origin = origin;
  }

  /**
   * Returns the SentryLevel
   *
   * @return the level
   */
  public @Nullable SentryLevel getLevel() {
    return level;
  }

  /**
   * Sets the level
   *
   * @param level the level
   */
  public void setLevel(@Nullable SentryLevel level) {
    this.level = level;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Breadcrumb that = (Breadcrumb) o;

    if ("http".equals(type)) {
      return httpBreadcrumbEquals(this, that);
    } else {
      return breadcrumbEquals(this, that);
    }
  }

  @Override
  public int hashCode() {
    if ("http".equals(type)) {
      return httpBreadcrumbHashCode(this);
    } else {
      return breadcrumbHashCode(this);
    }
  }

  /**
   * Default breadcrumb equality check that excludes data map comparison. Compares core breadcrumb
   * fields for general breadcrumb types.
   */
  @SuppressWarnings("JavaUtilDate")
  private static boolean breadcrumbEquals(
      final @NotNull Breadcrumb a, final @NotNull Breadcrumb b) {
    return a.getTimestamp().getTime() == b.getTimestamp().getTime()
        && Objects.equals(a.message, b.message)
        && Objects.equals(a.type, b.type)
        && Objects.equals(a.category, b.category)
        && Objects.equals(a.origin, b.origin)
        && a.level == b.level;
  }

  /**
   * The fields compared here correspond to those set by {@link #http(String, String, Integer)},
   * ensuring that breadcrumbs created via these factory methods are properly distinguished even
   * when they share the same timestamp.
   */
  private static boolean httpBreadcrumbEquals(
      final @NotNull Breadcrumb a, final @NotNull Breadcrumb b) {
    return breadcrumbEquals(a, b)
        && Objects.equals(a.getData("status_code"), b.getData("status_code"))
        && Objects.equals(a.getData("url"), b.getData("url"))
        && Objects.equals(a.getData("method"), b.getData("method"))
        && Objects.equals(a.getData("http.fragment"), b.getData("http.fragment"))
        && Objects.equals(a.getData("http.query"), b.getData("http.query"));
  }

  /** Default breadcrumb hash code that excludes data map. */
  @SuppressWarnings("JavaUtilDate")
  private static int breadcrumbHashCode(final @NotNull Breadcrumb breadcrumb) {
    return Objects.hash(
        breadcrumb.getTimestamp().getTime(),
        breadcrumb.message,
        breadcrumb.type,
        breadcrumb.category,
        breadcrumb.origin,
        breadcrumb.level);
  }

  /**
   * HTTP breadcrumb hash code that includes specific data fields for {@link #http(String, String,
   * Integer)} Breadcrumbs.
   */
  @SuppressWarnings("JavaUtilDate")
  private static int httpBreadcrumbHashCode(final @NotNull Breadcrumb breadcrumb) {
    return Objects.hash(
        breadcrumb.getTimestamp().getTime(),
        breadcrumb.message,
        breadcrumb.type,
        breadcrumb.category,
        breadcrumb.origin,
        breadcrumb.level,
        breadcrumb.getData("status_code"),
        breadcrumb.getData("url"),
        breadcrumb.getData("method"),
        breadcrumb.getData("http.fragment"),
        breadcrumb.getData("http.query"));
  }

  // region json

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
  @SuppressWarnings("JavaUtilDate")
  public int compareTo(@NotNull Breadcrumb o) {
    return nanos.compareTo(o.nanos);
  }

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String MESSAGE = "message";
    public static final String TYPE = "type";
    public static final String DATA = "data";
    public static final String CATEGORY = "category";
    public static final String ORIGIN = "origin";
    public static final String LEVEL = "level";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TIMESTAMP).value(logger, getTimestamp());
    if (message != null) {
      writer.name(JsonKeys.MESSAGE).value(message);
    }
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    writer.name(JsonKeys.DATA).value(logger, data);
    if (category != null) {
      writer.name(JsonKeys.CATEGORY).value(category);
    }
    if (origin != null) {
      writer.name(JsonKeys.ORIGIN).value(origin);
    }
    if (level != null) {
      writer.name(JsonKeys.LEVEL).value(logger, level);
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

  public static final class Deserializer implements JsonDeserializer<Breadcrumb> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Breadcrumb deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      @NotNull Date timestamp = DateUtils.getCurrentDateTime();
      String message = null;
      String type = null;
      @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
      String category = null;
      String origin = null;
      SentryLevel level = null;

      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TIMESTAMP:
            Date deserializedDate = reader.nextDateOrNull(logger);
            if (deserializedDate != null) {
              timestamp = deserializedDate;
            }
            break;
          case JsonKeys.MESSAGE:
            message = reader.nextStringOrNull();
            break;
          case JsonKeys.TYPE:
            type = reader.nextStringOrNull();
            break;
          case JsonKeys.DATA:
            Map<String, Object> deserializedData =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, Object>) reader.nextObjectOrNull());
            if (deserializedData != null) {
              data = deserializedData;
            }
            break;
          case JsonKeys.CATEGORY:
            category = reader.nextStringOrNull();
            break;
          case JsonKeys.ORIGIN:
            origin = reader.nextStringOrNull();
            break;
          case JsonKeys.LEVEL:
            try {
              level = new SentryLevel.Deserializer().deserialize(reader, logger);
            } catch (Exception exception) {
              logger.log(SentryLevel.ERROR, exception, "Error when deserializing SentryLevel");
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

      Breadcrumb breadcrumb = new Breadcrumb(timestamp);
      breadcrumb.message = message;
      breadcrumb.type = type;
      breadcrumb.data = data;
      breadcrumb.category = category;
      breadcrumb.origin = origin;
      breadcrumb.level = level;

      breadcrumb.setUnknown(unknown);
      reader.endObject();
      return breadcrumb;
    }
  }

  // endregion
}
