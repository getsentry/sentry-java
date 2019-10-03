package io.sentry;

import io.sentry.util.NonNull;
import java.util.ArrayList;
import java.util.List;

public class SentryOptions {
  static final SentryLevel DEFAULT_DIAGNOSTIC_LEVEL = SentryLevel.Debug;

  private List<EventProcessor> eventProcessors = new ArrayList<>();

  private String dsn;
  private boolean debug;
  private @NonNull ILogger logger = NoOpLogger.getInstance();
  private SentryLevel diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
  private ISerializer serializer;

  public void addEventProcessor(EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  public List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  public String getDsn() {
    return dsn;
  }

  public void setDsn(String dsn) {
    this.dsn = dsn;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public @NonNull ILogger getLogger() {
    return logger;
  }

  public void setLogger(ILogger logger) {
    this.logger = logger == null ? NoOpLogger.getInstance() : new DiagnosticLogger(this, logger);
  }

  public SentryLevel getDiagnosticLevel() {
    return diagnosticLevel;
  }

  public void setDiagnosticLevel(SentryLevel diagnosticLevel) {
    if (diagnosticLevel == null) {
      diagnosticLevel = DEFAULT_DIAGNOSTIC_LEVEL;
    }
    this.diagnosticLevel = diagnosticLevel;
  }

  public ISerializer getSerializer() {
    return serializer;
  }

  public void setSerializer(ISerializer serializer) {
    this.serializer = serializer;
  }
}
