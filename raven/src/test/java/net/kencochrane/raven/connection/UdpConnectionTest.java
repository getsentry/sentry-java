package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.marshaller.Marshaller;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.OutputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class UdpConnectionTest {
    private UdpConnection udpConnection;
    @Mock
    private Marshaller mockMarshaller;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        udpConnection = new UdpConnection("", "", "");
        udpConnection.setMarshaller(mockMarshaller);
    }

    @Test
    public void testContentMarshalled() throws Exception {
        Event event = new EventBuilder().build();

        udpConnection.send(event);

        verify(mockMarshaller).marshall(eq(event), any(OutputStream.class));
    }
}
