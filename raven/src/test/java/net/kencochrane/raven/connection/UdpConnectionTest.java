package net.kencochrane.raven.connection;

import mockit.Injectable;
import mockit.Verifications;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.OutputStream;

public class UdpConnectionTest {
    private UdpConnection udpConnection;
    @Injectable
    private Marshaller mockMarshaller;

    @BeforeMethod
    public void setUp() throws Exception {
        udpConnection = new UdpConnection("", "", "");
        udpConnection.setMarshaller(mockMarshaller);
    }

    @Test
    public void testContentMarshalled(@Injectable final Event event) throws Exception {
        udpConnection.send(event);

        new Verifications() {{
            mockMarshaller.marshall(event, (OutputStream) any);
        }};
    }
}
