package io.sentry.connection;

import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Connection using StdOut to sent marshalled events.
 */
public class OutputStreamConnection extends AbstractConnection {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final OutputStream outputStream;
    private Marshaller marshaller;

    /**
     * Creates a connection that sends event on an outputStream.
     *
     * @param outputStream stream on which the events are submitted.
     */
    public OutputStreamConnection(OutputStream outputStream) {
        super(null, null);
        this.outputStream = outputStream;
    }

    @Override
    protected synchronized void doSend(Event event) throws ConnectionException {
        try {
            outputStream.write("Sentry event:\n".getBytes(UTF_8));
            marshaller.marshall(event, outputStream);
            outputStream.write("\n".getBytes(UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new ConnectionException("Couldn't sent the event properly", e);
        }
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
