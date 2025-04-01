package io.sentry;

import io.sentry.protocol.User;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.StringUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Session implements JsonUnknown, JsonSerializable {

  /** Session state */
  public enum State {
    Ok,
    Exited,
    Crashed,
    Abnormal
  }

  /** started timestamp */
  private final @NotNull Date started;

  /** the timestamp */
  private @Nullable Date timestamp;

  /** the number of errors on the session */
  private final @NotNull AtomicInteger errorCount;

  /** The distinctId, did */
  private final @Nullable String distinctId;

  /** the SessionId, sid */
  private final @Nullable String sessionId;

  /** The session init flag */
  private @Nullable Boolean init;

  /** The session state */
  private @NotNull State status;

  /** The session sequence */
  private @Nullable Long sequence;

  /** The session duration (timestamp - started) */
  private @Nullable Double duration;

  /** the user's ip address */
  private final @Nullable String ipAddress;

  /** the user Agent */
  private @Nullable String userAgent;

  /** the environment */
  private final @Nullable String environment;

  /** the App's release */
  private final @NotNull String release;

  /** the Abnormal mechanism, e.g. what was the reason for session to become abnormal (ANR) */
  private @Nullable String abnormalMechanism;

  /** The session lock, ops should be atomic */
  private final @NotNull AutoClosableReentrantLock sessionLock = new AutoClosableReentrantLock();

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public Session(
      final @NotNull State status,
      final @NotNull Date started,
      final @Nullable Date timestamp,
      final int errorCount,
      final @Nullable String distinctId,
      final @Nullable String sessionId,
      final @Nullable Boolean init,
      final @Nullable Long sequence,
      final @Nullable Double duration,
      final @Nullable String ipAddress,
      final @Nullable String userAgent,
      final @Nullable String environment,
      final @NotNull String release,
      final @Nullable String abnormalMechanism) {
    this.status = status;
    this.started = started;
    this.timestamp = timestamp;
    this.errorCount = new AtomicInteger(errorCount);
    this.distinctId = distinctId;
    this.sessionId = sessionId;
    this.init = init;
    this.sequence = sequence;
    this.duration = duration;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.environment = environment;
    this.release = release;
    this.abnormalMechanism = abnormalMechanism;
  }

  public Session(
      @Nullable String distinctId,
      final @Nullable User user,
      final @Nullable String environment,
      final @NotNull String release) {
    this(
        State.Ok,
        DateUtils.getCurrentDateTime(),
        DateUtils.getCurrentDateTime(),
        0,
        distinctId,
        SentryUUID.generateSentryId(),
        true,
        null,
        null,
        (user != null ? user.getIpAddress() : null),
        null,
        environment,
        release,
        null);
  }

  public boolean isTerminated() {
    return status != State.Ok;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public @Nullable Date getStarted() {
    if (started == null) {
      return null;
    }
    return (Date) started.clone();
  }

  public @Nullable String getDistinctId() {
    return distinctId;
  }

  public @Nullable String getSessionId() {
    return sessionId;
  }

  public @Nullable String getIpAddress() {
    return ipAddress;
  }

  public @Nullable String getUserAgent() {
    return userAgent;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public @NotNull String getRelease() {
    return release;
  }

  public @Nullable Boolean getInit() {
    return init;
  }

  /** Used for migrating the init flag when an session is gonna be deleted. */
  @ApiStatus.Internal
  public void setInitAsTrue() {
    this.init = true;
  }

  public int errorCount() {
    return errorCount.get();
  }

  public @NotNull State getStatus() {
    return status;
  }

  public @Nullable Long getSequence() {
    return sequence;
  }

  public @Nullable Double getDuration() {
    return duration;
  }

  public @Nullable String getAbnormalMechanism() {
    return abnormalMechanism;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public @Nullable Date getTimestamp() {
    final Date timestampRef = timestamp;
    return timestampRef != null ? (Date) timestampRef.clone() : null;
  }

  /** Ends a session and update its values */
  public void end() {
    end(DateUtils.getCurrentDateTime());
  }

  /**
   * Ends a session and update its values
   *
   * @param timestamp the timestamp or null
   */
  public void end(final @Nullable Date timestamp) {
    try (final @NotNull ISentryLifecycleToken ignored = sessionLock.acquire()) {
      init = null;

      // at this state it might be Crashed already, so we don't check for it.
      if (status == State.Ok) {
        status = State.Exited;
      }

      if (timestamp != null) {
        this.timestamp = timestamp;
      } else {
        this.timestamp = DateUtils.getCurrentDateTime();
      }

      if (this.timestamp != null) {
        duration = calculateDurationTime(this.timestamp);
        sequence = getSequenceTimestamp(this.timestamp);
      }
    }
  }

  /**
   * Calculates the duration time in seconds timestamp (last update) - started
   *
   * @param timestamp the timestamp
   * @return duration in seconds
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  private double calculateDurationTime(final @NotNull Date timestamp) {
    long diff = Math.abs(timestamp.getTime() - started.getTime());
    return (double) diff / 1000; // duration in seconds
  }

  public boolean update(
      final @Nullable State status,
      final @Nullable String userAgent,
      final boolean addErrorsCount) {
    return update(status, userAgent, addErrorsCount, null);
  }

  /**
   * Updates the current session and set its values
   *
   * @param status the status
   * @param userAgent the userAgent
   * @param addErrorsCount true if should increase error count or not
   * @param abnormalMechanism the mechanism which caused the session to be abnormal
   * @return if the session has been updated
   */
  public boolean update(
      final @Nullable State status,
      final @Nullable String userAgent,
      final boolean addErrorsCount,
      final @Nullable String abnormalMechanism) {
    try (final @NotNull ISentryLifecycleToken ignored = sessionLock.acquire()) {
      boolean sessionHasBeenUpdated = false;
      if (status != null) {
        this.status = status;
        sessionHasBeenUpdated = true;
      }

      if (userAgent != null) {
        this.userAgent = userAgent;
        sessionHasBeenUpdated = true;
      }
      if (addErrorsCount) {
        errorCount.addAndGet(1);
        sessionHasBeenUpdated = true;
      }

      // if the session has experienced an Abnormal status at least once, it should count towards
      // the
      // Abnormal rate, therefore we do not want to overwrite it with `null`, even if it has
      // recovered
      if (abnormalMechanism != null) {
        this.abnormalMechanism = abnormalMechanism;
        sessionHasBeenUpdated = true;
      }

      if (sessionHasBeenUpdated) {
        init = null;
        timestamp = DateUtils.getCurrentDateTime();
        if (timestamp != null) {
          sequence = getSequenceTimestamp(timestamp);
        }
      }
      return sessionHasBeenUpdated;
    }
  }

  /**
   * Returns a logical clock.
   *
   * @param timestamp The timestamp
   * @return time stamp in milliseconds UTC
   */
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  private long getSequenceTimestamp(final @NotNull Date timestamp) {
    long sequence = timestamp.getTime();
    // if device has wrong date and time and it is nearly at the beginning of the epoch time.
    // when converting GMT to UTC may give a negative value.
    if (sequence < 0) {
      sequence = Math.abs(sequence);
    }
    return sequence;
  }

  /**
   * Ctor copy of the Session
   *
   * @return a copy of the Session
   */
  @SuppressWarnings("MissingOverride")
  public @NotNull Session clone() {
    return new Session(
        status,
        started,
        timestamp,
        errorCount.get(),
        distinctId,
        sessionId,
        init,
        sequence,
        duration,
        ipAddress,
        userAgent,
        environment,
        release,
        abnormalMechanism);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String SID = "sid";
    public static final String DID = "did";
    public static final String INIT = "init";
    public static final String STARTED = "started";
    public static final String STATUS = "status";
    public static final String SEQ = "seq";
    public static final String ERRORS = "errors";
    public static final String DURATION = "duration";
    public static final String TIMESTAMP = "timestamp";

    public static final String ATTRS = "attrs";
    public static final String RELEASE = "release";
    public static final String ENVIRONMENT = "environment";
    public static final String IP_ADDRESS = "ip_address";
    public static final String USER_AGENT = "user_agent";
    public static final String ABNORMAL_MECHANISM = "abnormal_mechanism";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (sessionId != null) {
      writer.name(JsonKeys.SID).value(sessionId);
    }
    if (distinctId != null) {
      writer.name(JsonKeys.DID).value(distinctId);
    }
    if (init != null) {
      writer.name(JsonKeys.INIT).value(init);
    }
    writer.name(JsonKeys.STARTED).value(logger, started);
    writer.name(JsonKeys.STATUS).value(logger, status.name().toLowerCase(Locale.ROOT));
    if (sequence != null) {
      writer.name(JsonKeys.SEQ).value(sequence);
    }
    writer.name(JsonKeys.ERRORS).value(errorCount.intValue());
    if (duration != null) {
      writer.name(JsonKeys.DURATION).value(duration);
    }
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    }
    if (abnormalMechanism != null) {
      writer.name(JsonKeys.ABNORMAL_MECHANISM).value(logger, abnormalMechanism);
    }
    writer.name(JsonKeys.ATTRS);
    writer.beginObject();
    writer.name(JsonKeys.RELEASE).value(logger, release);
    if (environment != null) {
      writer.name(JsonKeys.ENVIRONMENT).value(logger, environment);
    }
    if (ipAddress != null) {
      writer.name(JsonKeys.IP_ADDRESS).value(logger, ipAddress);
    }
    if (userAgent != null) {
      writer.name(JsonKeys.USER_AGENT).value(logger, userAgent);
    }
    writer.endObject();
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

  public static final class Deserializer implements JsonDeserializer<Session> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Session deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();

      Date started = null; // @NotNull
      Date timestamp = null;
      Integer errorCount = null; // @NotNull
      String distinctId = null;
      String sessionId = null;
      Boolean init = null;
      State status = null; // @NotNull
      Long sequence = null;
      Double duration = null;
      String ipAddress = null;
      String userAgent = null;
      String environment = null;
      String release = null; // @NotNull
      String abnormalMechanism = null;

      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SID:
            String sid = reader.nextStringOrNull();
            if (sid != null && (sid.length() == 36 || sid.length() == 32)) {
              sessionId = sid;
            } else {
              if (logger.isEnabled(SentryLevel.ERROR)) {
                logger.log(SentryLevel.ERROR, "%s sid is not valid.", sid);
              }
            }
            break;
          case JsonKeys.DID:
            distinctId = reader.nextStringOrNull();
            break;
          case JsonKeys.INIT:
            init = reader.nextBooleanOrNull();
            break;
          case JsonKeys.STARTED:
            started = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.STATUS:
            String statusValue = StringUtils.capitalize(reader.nextStringOrNull());
            if (statusValue != null) {
              status = State.valueOf(statusValue);
            }
            break;
          case JsonKeys.SEQ:
            sequence = reader.nextLongOrNull();
            break;
          case JsonKeys.ERRORS:
            errorCount = reader.nextIntegerOrNull();
            break;
          case JsonKeys.DURATION:
            duration = reader.nextDoubleOrNull();
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.ABNORMAL_MECHANISM:
            abnormalMechanism = reader.nextStringOrNull();
            break;
          case JsonKeys.ATTRS:
            reader.beginObject();
            while (reader.peek() == JsonToken.NAME) {
              final String nextAttrName = reader.nextName();
              switch (nextAttrName) {
                case JsonKeys.RELEASE:
                  release = reader.nextStringOrNull();
                  break;
                case JsonKeys.ENVIRONMENT:
                  environment = reader.nextStringOrNull();
                  break;
                case JsonKeys.IP_ADDRESS:
                  ipAddress = reader.nextStringOrNull();
                  break;
                case JsonKeys.USER_AGENT:
                  userAgent = reader.nextStringOrNull();
                  break;
                default:
                  reader.skipValue(); // Ignore unknown properties in attrs, as this is flattend
              }
            }
            reader.endObject();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (status == null) {
        throw missingRequiredFieldException(JsonKeys.STATUS, logger);
      }
      if (started == null) {
        throw missingRequiredFieldException(JsonKeys.STARTED, logger);
      }
      if (errorCount == null) {
        throw missingRequiredFieldException(JsonKeys.ERRORS, logger);
      }
      if (release == null) {
        throw missingRequiredFieldException(JsonKeys.RELEASE, logger);
      }
      Session session =
          new Session(
              status,
              started,
              timestamp,
              errorCount,
              distinctId,
              sessionId,
              init,
              sequence,
              duration,
              ipAddress,
              userAgent,
              environment,
              release,
              abnormalMechanism);
      session.setUnknown(unknown);
      reader.endObject();
      return session;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, message, exception);
      }
      return exception;
    }
  }
}
