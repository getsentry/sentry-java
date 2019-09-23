package io.sentry;

import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class SentryEvent {
  private SentryId eventId;
  private Date timestamp;
  private Throwable throwable;
  private Message message;

  SentryEvent(SentryId eventId, Date timestamp) {
    this.eventId = eventId;
    this.timestamp = timestamp;
  }

  public SentryEvent(Throwable throwable) {
    this();
    this.throwable = throwable;
  }

  Throwable getThrowable() {
    return throwable;
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
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    df.setTimeZone(tz);
    return df.format(timestamp);
  }

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message;
  }
}
