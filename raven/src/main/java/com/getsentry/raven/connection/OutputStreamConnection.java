package com.getsentry.raven.connection;

import com.google.common.base.Charsets;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.marshaller.Marshaller;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Connection using StdOut to sent marshalled events.
 */
public class OutputStreamConnection extends AbstractConnection {
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
            outputStream.write("Raven event:\n".getBytes(Charsets.UTF_8));
            marshaller.marshall(event, outputStream);
            outputStream.write("\n".getBytes(Charsets.UTF_8));
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
