package net.kencochrane.raven;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import java.net.SocketException;

import static org.junit.Assert.assertEquals;

/**
 * Test cases for {@link AsyncTransport}.
 */
public class AsyncTransportTest {

    private UdpTransportTest.CollectingSocket socket;
    private long timestamp = System.currentTimeMillis();

    @Test
    public void ultimatelySent() throws Exception {
        String messageBody = "MessageBodyDoesNotReallyMatter";
        String authHeader = Transport.buildAuthHeader(timestamp, "public");

        // Actual testing
        SentryDsn dsn = SentryDsn.build("async+udp://public:private@host:9999/1");
        AsyncTransport transport = AsyncTransport.build(new Transport.Udp(dsn));
        transport.start();
        for (int i = 0; i < 500; ++i) {
            transport.send(messageBody, timestamp);
        }
        transport.stop();
        Thread.sleep(AsyncTransport.WAIT_FOR_SHUTDOWN);

        // Verify
        assertEquals(500, socket.packets.size());
        for (int i = 0; i < 500; ++i) {
            byte[] data = socket.packets.get(i).getData();
            assertEquals(authHeader + "\n\n" + messageBody, Utils.fromUtf8(data));
        }
    }

    @Before
    public void setUp() throws SocketException {
        socket = new UdpTransportTest.CollectingSocket();
        new Expectations() {
            @Mocked("createSocket")
            Transport.Udp m;

            {
                m.createSocket("host", 9999);
                returns(socket);
            }
        };
    }

}
