package io.sentry;

import io.sentry.util.CollectionUtils;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Series of application events */
public final class Breadcrumb implements Cloneable, IUnknownPropertiesConsumer {

  /** A timestamp representing when the breadcrumb occurred. */
  private final @Nullable Date timestamp;

  /** If a message is provided, its rendered as text and the whitespace is preserved. */
  private @Nullable String message;

  /** The type of breadcrumb. */
  private @Nullable String type;

  /** Data associated with this breadcrumb. */
  private @NotNull Map<String, Object> data = new ConcurrentHashMap<>();

  /** Dotted strings that indicate what the crumb is or where it comes from. */
  private @Nullable String category;

  /** The level of the event. */
  private @Nullable SentryLevel level;

  /** the unknown fields of breadcrumbs, internal usage only */
  private @Nullable Map<String, Object> unknown;

  /**
   * Breadcrumb ctor
   *
   * @param timestamp the timestamp
   */
  Breadcrumb(final @Nullable Date timestamp) {
    this.timestamp = timestamp;
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
    breadcrumb.setType("http");
    breadcrumb.setCategory("http");
    breadcrumb.setData("url", url);
    breadcrumb.setData("method", method.toUpperCase(Locale.getDefault()));
    return breadcrumb;
  }

  /** Breadcrumb ctor */
  public Breadcrumb() {
    this(DateUtils.getCurrentDateTimeOrNull());
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
   * Returns the Breadcrumb's timestamp
   *
   * @return the timestamp
   */
  @SuppressWarnings("JdkObsolete")
  public @NotNull Date getTimestamp() {
    return (Date) timestamp.clone();
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
  @NotNull
  Map<String, Object> getData() {
    return data;
  }

  /**
   * Returns the value of data[key] or null
   *
   * @param key the key
   * @return the value or null
   */
  @Nullable
  public Object getData(final @NotNull String key) {
    return data.get(key);
  }

  /**
   * Sets an entry to the data's map
   *
   * @param key the key
   * @param value the value
   */
  public void setData(@NotNull String key, @NotNull Object value) {
    data.put(key, value);
  }

  /**
   * Removes an entry from the data's map
   *
   * @param key the key
   */
  public void removeData(@NotNull String key) {
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

  /**
   * Sets the unknown fields, internal usage only
   *
   * @param unknown the unknown's map
   */
  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(@Nullable Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Returns the unknown's map, internal usage only
   *
   * @return the unknown map
   */
  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  /**
   * Clones the breadcrumb aka deep copy
   *
   * @return the cloned breadcrumb
   * @throws CloneNotSupportedException if a breadcrumb is not cloneable
   */
  @Override
  public @NotNull Breadcrumb clone() throws CloneNotSupportedException {
    final Breadcrumb clone = (Breadcrumb) super.clone();

    clone.data = CollectionUtils.shallowCopy(data);
    clone.unknown = CollectionUtils.shallowCopy(unknown);

    final SentryLevel levelRef = level;
    clone.level =
        levelRef != null ? SentryLevel.valueOf(levelRef.name().toUpperCase(Locale.ROOT)) : null;

    return clone;
  }
}
