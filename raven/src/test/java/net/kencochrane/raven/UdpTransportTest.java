package net.kencochrane.raven;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test cases for {@link Transport.Udp}.
 */
public class UdpTransportTest {

    private CollectingSocket socket;
    private String messageBody = "MessageBodyDoesNotReallyMatter";
    private long timestamp = System.currentTimeMillis();
    private String authHeader;

    @Test
    public void verifyPacketStructure() throws IOException {
        authHeader = Transport.buildAuthHeader(timestamp, "public");

        // Actual testing
        SentryDsn dsn = SentryDsn.build("udp://public:private@host:9999/1");
        Transport.Udp transport = new Transport.Udp(dsn);
        transport.send(messageBody, timestamp);

        // Verify
        assertEquals(1, socket.packets.size());
        byte[] data = socket.packets.get(0).getData();
        assertEquals(authHeader + "\n\n" + messageBody, Utils.fromUtf8(data));
    }

    @Test
    public void verifyPacketStructure_withAuthHeader() throws IOException {
        String signature = Client.sign(messageBody, timestamp, "private");
        authHeader = Transport.buildAuthHeader(signature, timestamp, "public");

        // Actual testing
        SentryDsn dsn = SentryDsn.build("udp://public:private@host:9999/1?" + Transport.Option.INCLUDE_SIGNATURE + "=true");
        Transport.Udp transport = new Transport.Udp(dsn);
        transport.send(messageBody, timestamp);

        // Verify
        assertEquals(1, socket.packets.size());
        byte[] data = socket.packets.get(0).getData();
        assertEquals(authHeader + "\n\n" + messageBody, Utils.fromUtf8(data));
    }

    @Before
    public void setUp() throws SocketException {
        socket = new CollectingSocket();
        new Expectations() {
            @Mocked("createSocket")
            Transport.Udp m;

            {
                m.createSocket("host", 9999);
                returns(socket);
            }
        };
    }

    public static class CollectingSocket extends DatagramSocket {

        public final List<DatagramPacket> packets = new LinkedList<DatagramPacket>();

        public CollectingSocket() throws SocketException {
            super();
        }

        @Override
        public void connect(SocketAddress socketAddress) throws SocketException {
            // Do nothing
        }

        @Override
        public void send(DatagramPacket packet) throws IOException {
            packets.add(packet);
        }
    }

}
