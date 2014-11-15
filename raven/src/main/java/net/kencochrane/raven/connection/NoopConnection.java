package net.kencochrane.raven.connection;

import com.google.common.base.Charsets;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;

import java.io.IOException;
import java.io.OutputStream;

public class NoopConnection extends AbstractConnection {
    public NoopConnection() {
        super(null, null);
    }

    @Override
    protected void doSend(Event event) throws ConnectionException {
    }

    @Override
    public void close() throws IOException {
    }
}
