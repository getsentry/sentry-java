package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLockReason;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A process thread of an event.
 *
 * <p>The Threads Interface specifies threads that were running at the time an event happened. These
 * threads can also contain stack traces.
 *
 * <p>An event may contain one or more threads in an attribute named `threads`.
 *
 * <p>The following example illustrates the threads part of the event payload and omits other
 * attributes for simplicity.
 *
 * <p>```json { "threads": { "values": [ { "id": "0", "name": "main", "crashed": true, "stacktrace":
 * {} } ] } } ```
 */
public final class SentryThread implements JsonUnknown, JsonSerializable {
  private @Nullable Long id;
  private @Nullable Integer priority;
  private @Nullable String name;
  private @Nullable String state;
  private @Nullable Boolean crashed;
  private @Nullable Boolean current;
  private @Nullable Boolean daemon;
  private @Nullable Boolean main;
  private @Nullable SentryStackTrace stacktrace;

  private @Nullable Map<String, SentryLockReason> heldLocks;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  /**
   * Gets the Id of the thread.
   *
   * @return the thread id.
   */
  public @Nullable Long getId() {
    return id;
  }

  /**
   * Sets the Id of the thread.
   *
   * @param id the thread id.
   */
  public void setId(final @Nullable Long id) {
    this.id = id;
  }

  /**
   * Gets the name of the thread.
   *
   * @return the name of the thread.
   */
  public @Nullable String getName() {
    return name;
  }

  /**
   * Sets the name of the thread.
   *
   * @param name the name of the thread.
   */
  public void setName(final @Nullable String name) {
    this.name = name;
  }

  /**
   * Gets whether the crash happened on this thread.
   *
   * @return whether it was the crashed thread.
   */
  public @Nullable Boolean isCrashed() {
    return crashed;
  }

  /**
   * Sets whether the crash happened on this thread.
   *
   * @param crashed whether it was the crashed thread.
   */
  public void setCrashed(final @Nullable Boolean crashed) {
    this.crashed = crashed;
  }

  /**
   * Get an optional flag to indicate that the thread was in the foreground.
   *
   * @return whether the thread was in the foreground.
   */
  public @Nullable Boolean isCurrent() {
    return current;
  }

  /**
   * Sets an optional flag to indicate that the thread was in the foreground.
   *
   * @param current whether the thread was in the foreground.
   */
  public void setCurrent(final @Nullable Boolean current) {
    this.current = current;
  }

  /**
   * Gets the stacktrace of the thread.
   *
   * @return the thread stacktrace.
   */
  public @Nullable SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stacktrace of the thread.
   *
   * @param stacktrace the thread stacktrace.
   */
  public void setStacktrace(final @Nullable SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }

  /**
   * Gets the priority of the thread.
   *
   * @return the thread priority.
   */
  public @Nullable Integer getPriority() {
    return priority;
  }

  /**
   * Sets the priority of the thread.
   *
   * @param priority of the thread.
   */
  public void setPriority(final @Nullable Integer priority) {
    this.priority = priority;
  }

  /**
   * Gets if this thread is a daemon thread.
   *
   * @return if this is a daemon thread.
   */
  public @Nullable Boolean isDaemon() {
    return daemon;
  }

  /**
   * Sets if this is a daemon thread.
   *
   * @param daemon true if the thread is daemon thread. Otherwise false.
   */
  public void setDaemon(final @Nullable Boolean daemon) {
    this.daemon = daemon;
  }

  /**
   * If applicable, a flag indicating whether the thread was responsible for rendering the user
   * interface. On mobile platforms this is oftentimes referred to as the "main thread" or "ui
   * thread".
   *
   * @return if its the main thread or not
   */
  @Nullable
  public Boolean isMain() {
    return main;
  }

  /**
   * If applicable, a flag indicating whether the thread was responsible for rendering the user
   * interface. On mobile platforms this is oftentimes referred to as the "main thread" or "ui
   * thread".
   *
   * @param main if its the main thread or not
   */
  public void setMain(final @Nullable Boolean main) {
    this.main = main;
  }

  /**
   * Gets the state of the thread.
   *
   * @return the state of the thread.
   */
  public @Nullable String getState() {
    return state;
  }

  /**
   * Sets the state of the thread.
   *
   * @param state the state of the thread.
   */
  public void setState(final @Nullable String state) {
    this.state = state;
  }

  /**
   * Gets locks held by this thread.
   *
   * @return locks held by this thread
   */
  public @Nullable Map<String, SentryLockReason> getHeldLocks() {
    return heldLocks;
  }

  /**
   * Sets locks held by this thread.
   *
   * @param heldLocks list of locks held by this thread
   */
  public void setHeldLocks(final @Nullable Map<String, SentryLockReason> heldLocks) {
    this.heldLocks = heldLocks;
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

  public static final class JsonKeys {
    public static final String ID = "id";
    public static final String PRIORITY = "priority";
    public static final String NAME = "name";
    public static final String STATE = "state";
    public static final String CRASHED = "crashed";
    public static final String CURRENT = "current";
    public static final String DAEMON = "daemon";
    public static final String MAIN = "main";
    public static final String STACKTRACE = "stacktrace";
    public static final String HELD_LOCKS = "held_locks";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (id != null) {
      writer.name(JsonKeys.ID).value(id);
    }
    if (priority != null) {
      writer.name(JsonKeys.PRIORITY).value(priority);
    }
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (state != null) {
      writer.name(JsonKeys.STATE).value(state);
    }
    if (crashed != null) {
      writer.name(JsonKeys.CRASHED).value(crashed);
    }
    if (current != null) {
      writer.name(JsonKeys.CURRENT).value(current);
    }
    if (daemon != null) {
      writer.name(JsonKeys.DAEMON).value(daemon);
    }
    if (main != null) {
      writer.name(JsonKeys.MAIN).value(main);
    }
    if (stacktrace != null) {
      writer.name(JsonKeys.STACKTRACE).value(logger, stacktrace);
    }
    if (heldLocks != null) {
      writer.name(JsonKeys.HELD_LOCKS).value(logger, heldLocks);
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

  public static final class Deserializer implements JsonDeserializer<SentryThread> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryThread deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      SentryThread sentryThread = new SentryThread();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ID:
            sentryThread.id = reader.nextLongOrNull();
            break;
          case JsonKeys.PRIORITY:
            sentryThread.priority = reader.nextIntegerOrNull();
            break;
          case JsonKeys.NAME:
            sentryThread.name = reader.nextStringOrNull();
            break;
          case JsonKeys.STATE:
            sentryThread.state = reader.nextStringOrNull();
            break;
          case JsonKeys.CRASHED:
            sentryThread.crashed = reader.nextBooleanOrNull();
            break;
          case JsonKeys.CURRENT:
            sentryThread.current = reader.nextBooleanOrNull();
            break;
          case JsonKeys.DAEMON:
            sentryThread.daemon = reader.nextBooleanOrNull();
            break;
          case JsonKeys.MAIN:
            sentryThread.main = reader.nextBooleanOrNull();
            break;
          case JsonKeys.STACKTRACE:
            sentryThread.stacktrace =
                reader.nextOrNull(logger, new SentryStackTrace.Deserializer());
            break;
          case JsonKeys.HELD_LOCKS:
            final Map<String, SentryLockReason> heldLocks =
                reader.nextMapOrNull(logger, new SentryLockReason.Deserializer());
            if (heldLocks != null) {
              sentryThread.heldLocks = new HashMap<>(heldLocks);
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
      sentryThread.setUnknown(unknown);
      reader.endObject();
      return sentryThread;
    }
  }

  // endregion
}
