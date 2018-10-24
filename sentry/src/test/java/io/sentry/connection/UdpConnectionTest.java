package io.sentry.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.BaseTest;
import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import mockit.Tested;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.LinkedHashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class UdpConnectionTest extends BaseTest {

    private ObjectMapper om = new ObjectMapper();

    @Tested
    private UdpConnection udpConnection = new UdpConnection(new Dsn("udp://public:private@localhost:1234/1"));


    @Test
    @SuppressWarnings("unchecked")
    public void testUdpSend() throws Exception {
        EventBuilder eventBuilder = new EventBuilder()
                .withLevel(Event.Level.DEBUG)
                .withChecksum("checksum");

        final DatagramSocket ds = new DatagramSocket(1234);
        byte[] receive = new byte[65535];
        final DatagramPacket datagramPacket = new DatagramPacket(receive, receive.length);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ds.receive(datagramPacket);
                } catch (IOException e) {
                    fail();
                }
            }
        });
        t.start();
        udpConnection.send(eventBuilder.build());
        t.join(100);
        LinkedHashMap<String, Object> event = (LinkedHashMap<String, Object>) om.readValue(receive, Object.class);
        assertEquals(event.get("checksum"), "checksum", "Checksum does not match");
        assertEquals(event.get("level"), "debug", "Level does not match");
    }

}
