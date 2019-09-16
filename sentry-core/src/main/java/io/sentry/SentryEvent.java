package io.sentry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class SentryEvent {
  private UUID eventUuid;
  private Date timestamp;

  SentryEvent(UUID eventUuid, Date timestamp) {
    this.eventUuid = eventUuid;
    this.timestamp = timestamp;
  }

  public SentryEvent() {
    this(UUID.randomUUID(), Calendar.getInstance().getTime());
  }

  public UUID getEventId() {
    return eventUuid;
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
}
