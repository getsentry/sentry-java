package io.sentry;

import java.util.ArrayList;
import java.util.List;

public class SentryOptions {
  private List<EventProcessor> eventProcessors = new ArrayList<>();

  private String dsn;

  public void AddEventProcessor(EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  public String getDsn() {
    return dsn;
  }

  public void setDsn(String dsn) {
    this.dsn = dsn;
  }
}
