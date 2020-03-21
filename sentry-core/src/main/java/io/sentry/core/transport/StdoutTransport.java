package io.sentry.core.transport;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEvent;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;

public final class StdoutTransport implements ITransport {
  private final ISerializer serializer;

  public StdoutTransport(final ISerializer serializer) {
    this.serializer = serializer;
  }

  @Override
  public TransportResult send(SentryEvent event) throws IOException {
    Writer outputStreamWriter = new OutputStreamWriter(System.out, Charset.forName("UTF-8"));
    PrintWriter printWriter = new PrintWriter(outputStreamWriter);

    serializer.serialize(event, printWriter);

    return TransportResult.success();
  }

  @Override
  public void close() {}
}
