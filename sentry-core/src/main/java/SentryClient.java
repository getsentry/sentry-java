import java.util.UUID;

public class SentryClient {
  private SentryOptions options;

  public SentryClient(SentryOptions options) {
    this.options = options;
  }

  public UUID captureEvent(SentryEvent event) {
    return UUID.randomUUID();
  }
}
