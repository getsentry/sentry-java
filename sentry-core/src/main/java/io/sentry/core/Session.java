package io.sentry.core;

import io.sentry.core.protocol.User;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

public final class Session {

  public enum State {
    Ok,
    Exited,
    Crashed,
    Abnormal
  }

  private Date started;
  private Date timestamp;
  private final AtomicInteger errorCount = new AtomicInteger(0);
  private String deviceId; // did, distinctId
  private UUID sessionId; // sid
  private Boolean init;
  private State status;
  private Long sequence;
  private Double duration;
  private User user;

  // attrs
  private String ipAddress;
  private String userAgent;
  private String environment;
  private String release;

  private final @NotNull Object sessionLock = new Object();

  public Date getStarted() {
    return started;
  }

  public void setStarted(Date started) {
    this.started = started;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public void setSessionId(UUID sessionId) {
    this.sessionId = sessionId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public String getRelease() {
    return release;
  }

  public Boolean getInit() {
    return init;
  }

  public void setInit(Boolean init) {
    this.init = init;
  }

  public int errorCount() {
    return errorCount.get();
  }

  public void setErrorCount(int errorCount) {
    this.errorCount.set(errorCount);
  }

  public State getStatus() {
    return status;
  }

  public void setStatus(State status) {
    this.status = status;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
  }

  public Double getDuration() {
    return duration;
  }

  public void setDuration(Double duration) {
    this.duration = duration;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public synchronized void start(
      final String release, final String environment, final User user, final String distinctId) {
    synchronized (sessionLock) {
      init = true;
      sequence = 0L;

      if (sessionId == null) {
        sessionId = UUID.randomUUID();
      }

      if (started == null) {
        started = DateUtils.getCurrentDateTime();
      }

      if (release != null) {
        this.release = release;
      }

      if (environment != null) {
        this.environment = environment;
      }

      if (user != null) {
        this.user = user;
        if (this.user.getIpAddress() != null) {
          ipAddress = this.user.getIpAddress();
        }
      }

      if (distinctId != null) {
        this.deviceId = distinctId;
      }

      if (status == null) {
        status = State.Ok;
      }
    }
  }

  private void updateStatus() {
    // at this state it might be Crashed already, so we don't check for it.
    if (status == State.Ok && errorCount.get() > 0) {
      status = State.Abnormal;
    }

    if (status == State.Ok) {
      status = State.Exited;
    }
  }

  public void end() {
    synchronized (sessionLock) {
      init = null;
      updateStatus();
      timestamp = DateUtils.getCurrentDateTime();

      long diff =
          Math.abs(timestamp.getTime() - started.getTime()); // do we need to subtract idle time?
      duration = (double) diff;
      sequence = System.currentTimeMillis();
    }
  }

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
        sequence = System.currentTimeMillis();
      }
      return sessionHasBeenUpdated;
    }
  }
}
