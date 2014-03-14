package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UdpConnectionTest {
    @Injectable
    private final String hostname = "127.0.0.1";
    @Injectable
    private final String publicKey = "44850120-9d2a-451b-8e00-998bddaa2800";
    @Injectable
    private final String secretKey = "1de38091-6e8c-42df-8298-cf7f8098617a";
    @Tested
    private UdpConnection udpConnection;
    @Injectable
    private Marshaller mockMarshaller;
    @Mocked
    private DatagramSocket mockDatagramSocket;

    @BeforeMethod
    public void setUp() throws Exception {
        udpConnection = null;
    }

    @Test
    public void testConnectionWorkingWithProperHost(@Injectable("customHostname") final String mockHostname,
                                                    @Injectable("1234") final int mockPort) throws Exception {
        new UdpConnection(mockHostname, mockPort, publicKey, secretKey);

        new Verifications() {{
            InetSocketAddress generatedAddress;
            mockDatagramSocket.connect(generatedAddress = withCapture());
            assertThat(generatedAddress.getHostName(), is(mockHostname));
            assertThat(generatedAddress.getPort(), is(mockPort));
        }};
    }

    @Test
    public void testConnectionDefaultPortIsWorking(@Injectable("customHostname") final String mockHostname)
            throws Exception {
        new UdpConnection(mockHostname, publicKey, secretKey);

        new Verifications() {{
            InetSocketAddress generatedAddress;
            mockDatagramSocket.connect(generatedAddress = withCapture());
            assertThat(generatedAddress.getPort(), is(UdpConnection.DEFAULT_UDP_PORT));
        }};
    }

    @Test
    public void udpMessageSentWorksAsExpected(@Injectable final Event event) throws Exception {
        final String marshalledContent = "marshalledContent";
        new NonStrictExpectations() {{
            mockMarshaller.marshall(event, (OutputStream) any);
            result = new Delegate<Void>() {
                public void marshall(Event event, OutputStream os) throws Exception {
                    os.write(marshalledContent.getBytes("UTF-8"));
                }
            };
        }};
        udpConnection.send(event);

        new Verifications() {{
            DatagramPacket capturedPacket;
            mockDatagramSocket.send(capturedPacket = withCapture());
            assertThat(new String(capturedPacket.getData(), "UTF-8"),
                    is(udpConnection.getAuthHeader() + "\n\n" + marshalledContent));
        }};
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void marshallingIssuePreventSend(@Injectable final Event event) throws Exception {
        new NonStrictExpectations() {{
            mockMarshaller.marshall(event, (OutputStream) any);
            result = new RuntimeException("Marshalling doesn't work");
        }};
        udpConnection.send(event);
    }

    @Test
    public void testContentMarshalled(@Injectable final Event event) throws Exception {
        udpConnection.send(event);

        new Verifications() {{
            mockMarshaller.marshall(event, (OutputStream) any);
        }};
    }
}
