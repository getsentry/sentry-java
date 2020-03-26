package io.sentry.core;

import io.sentry.core.protocol.User;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Session {

  /** Session state */
  public enum State {
    Ok,
    Exited,
    Crashed,
    Abnormal
  }

  /** started timestamp */
  private @Nullable Date started;

  /** the timestamp */
  private @Nullable Date timestamp;

  /** the number of errors on the session */
  private final @NotNull AtomicInteger errorCount;

  /** The distinctId, did */
  private final @Nullable String distinctId;

  /** the SessionId, sid */
  private final @Nullable UUID sessionId;

  /** The session init flag */
  private @Nullable Boolean init;

  /** The session state */
  private @Nullable State status;

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
  private final @Nullable String release;

  /** The session lock, ops should be atomic */
  private final @NotNull Object sessionLock = new Object();

  public Session(
      final @Nullable State status,
      final @Nullable Date started,
      final @Nullable Date timestamp,
      final int errorCount,
      final @Nullable String distinctId,
      final @Nullable UUID sessionId,
      final @Nullable Boolean init,
      final @Nullable Long sequence,
      final @Nullable Double duration,
      final @Nullable String ipAddress,
      final @Nullable String userAgent,
      final @Nullable String environment,
      final @Nullable String release) {
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
  }

  public Session(
      @Nullable String distinctId,
      final @Nullable User user,
      final @Nullable String environment,
      final @Nullable String release) {
    this(
        State.Ok,
        DateUtils.getCurrentDateTime(),
        null,
        0,
        distinctId,
        UUID.randomUUID(),
        true,
        0L,
        null,
        (user != null ? user.getIpAddress() : null),
        null,
        environment,
        release);
  }

  public Date getStarted() {
    final Date startedRef = started;
    return startedRef != null ? (Date) startedRef.clone() : null;
  }

  public @Nullable String getDistinctId() {
    return distinctId;
  }

  public @Nullable UUID getSessionId() {
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

  public @Nullable String getRelease() {
    return release;
  }

  public @Nullable Boolean getInit() {
    return init;
  }

  public int errorCount() {
    return errorCount.get();
  }

  public @Nullable State getStatus() {
    return status;
  }

  public @Nullable Long getSequence() {
    return sequence;
  }

  public @Nullable Double getDuration() {
    return duration;
  }

  public Date getTimestamp() {
    final Date timestampRef = timestamp;
    return timestampRef != null ? (Date) timestampRef.clone() : null;
  }

  /** Updated the session status based on status and errorcount */
  private void updateStatus() {
    // at this state it might be Crashed already, so we don't check for it.
    if (status == State.Ok && errorCount.get() > 0) {
      status = State.Abnormal;
    }

    if (status == State.Ok) {
      status = State.Exited;
    }

    // fallback if status is null
    if (status == null) {
      // its ending a session and we don't have the current status, set it as Abnormal
      status = State.Abnormal;
    }
  }

  /** Ends a session and update its values */
  public void end() {
    synchronized (sessionLock) {
      init = null;
      updateStatus();
      timestamp = DateUtils.getCurrentDateTime();

      // fallback if started is null
      if (started == null) {
        started = timestamp;
      }

      duration = calculateDurationTime(timestamp, started);
      sequence = getSequenceTimestamp();
    }
  }

  /**
   * Calculates the duration time in seconds timestamp (last update) - started
   *
   * @param timestamp the timestamp
   * @param started the started timestamp
   * @return duration in seconds
   */
  private Double calculateDurationTime(final @NotNull Date timestamp, final @NotNull Date started) {
    long diff = Math.abs(timestamp.getTime() - started.getTime());
    return (double) diff / 1000; // duration in seconds
  }

  /**
   * Updates the current session and set its values
   *
   * @param status the status
   * @param userAgent the userAgent
   * @param addErrorsCount true if should increase error count or not
   * @return if the session has been updated
   */
  public boolean update(final State status, final String userAgent, boolean addErrorsCount) {
    synchronized (sessionLock) {
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

      if (sessionHasBeenUpdated) {
        init = null;
        timestamp = DateUtils.getCurrentDateTime();
        sequence = getSequenceTimestamp();
      }
      return sessionHasBeenUpdated;
    }
  }

  /**
   * Returns a logical clock.
   *
   * @return time stamp in milliseconds UTC
   */
  private Long getSequenceTimestamp() {
    return DateUtils.getCurrentDateTime().getTime();
  }
}
