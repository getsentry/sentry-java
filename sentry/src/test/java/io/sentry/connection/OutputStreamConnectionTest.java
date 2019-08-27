package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OutputStreamConnectionTest extends BaseTest {
    private OutputStreamConnection outputStreamConnection = null;
    private Marshaller mockMarshaller = null;
    private OutputStream mockOutputStream = null;

    @Before
    public void setup() {
        mockOutputStream = mock(OutputStream.class);
        mockMarshaller = mock(Marshaller.class);

        outputStreamConnection = new OutputStreamConnection(mockOutputStream);
        outputStreamConnection.setMarshaller(mockMarshaller);
    }

    @Test
    public void testContentMarshalled() throws Exception {
        Event mockEvent = mock(Event.class);
        outputStreamConnection.send(mockEvent);
        verify(mockMarshaller).marshall(eq(mockEvent), eq(mockOutputStream));
    }

    @Test
    public void testClose() throws Exception {
        outputStreamConnection.close();
        verify(mockOutputStream).close();
    }
}
