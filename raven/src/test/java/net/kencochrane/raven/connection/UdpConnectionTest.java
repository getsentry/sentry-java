package net.kencochrane.raven.connection;

import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetAddress;

public class UdpConnectionTest {
    @Injectable
    private final String hostname = "127.0.0.1";
    @Injectable
    private final int port = 1234;
    @Injectable
    private final String publicKey = "44850120-9d2a-451b-8e00-998bddaa2800";
    @Injectable
    private final String secretKey = "1de38091-6e8c-42df-8298-cf7f8098617a";
    @Tested
    private UdpConnection udpConnection;
    @Injectable
    private Marshaller mockMarshaller;

    @BeforeMethod
    public void setUp() throws Exception {
        udpConnection = null;
    }

    @Test
    public void testContentMarshalled(@Injectable final Event event) throws Exception {
        udpConnection.send(event);

        new Verifications() {{
            mockMarshaller.marshall(event, (OutputStream) any);
        }};
    }
}
