package net.kencochrane.raven.connection;

import com.google.common.base.Charsets;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Connection using StdOut to sent marshalled events.
 */
public class OutputStreamConnection extends AbstractConnection {
    private final OutputStream outputStream;
    private Marshaller marshaller;

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
