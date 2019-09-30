package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryThread;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SentryEvent extends Scope {
  private SentryId eventId;
  private Date timestamp;
  private Throwable throwable;
  private Message message;
  private String serverName;
  private String platform;
  private String release;
  private String logger;
  private SentryValues<SentryThread> threads;
  private SentryValues<SentryException> exceptions;

  SentryEvent(SentryId eventId, Date timestamp) {
    this.eventId = eventId;
    this.timestamp = timestamp;
  }

  public SentryEvent(Throwable throwable) {
    this();
    this.throwable = throwable;
  }

  public SentryEvent() {
    this(new SentryId(), Calendar.getInstance().getTime());
  }

  public SentryId getEventId() {
    return eventId;
  }

  public Date getTimestamp() {
    return (Date) timestamp.clone();
  }

  String getTimestampIsoFormat() {
    return DateUtils.getTimestampIsoFormat(timestamp);
  }

  Throwable getThrowable() {
    return throwable;
  }

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getRelease() {
    return release;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public String getLogger() {
    return logger;
  }

  public void setLogger(String logger) {
    this.logger = logger;
  }

  public List<SentryThread> getThreads() {
    return threads.getValues();
  }

  public void setThreads(List<SentryThread> threads) {
    this.threads = new SentryValues<SentryThread>(threads);
  }

  public List<SentryException> getExceptions() {
    return exceptions.getValues();
  }

  public void setExceptions(List<SentryException> exceptions) {
    this.exceptions = new SentryValues<SentryException>(exceptions);
  }
}
