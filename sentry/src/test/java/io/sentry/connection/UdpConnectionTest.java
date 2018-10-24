package io.sentry.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.BaseTest;
import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;

public class UdpConnectionTest extends BaseTest {


    @Injectable
    private Dsn dsn = new Dsn("udp://public:private@localhost:1234/1");

    @Injectable
    private int maxMessageSize = 100;

    @Injectable
    private DatagramSocket datagramSocket = null;

    @Tested
    private UdpConnection udpConnection;


    @Test
    public void testDsnParsing() {
        UdpConnection connection = new UdpConnection(new Dsn("udp://public:private@localhost:1234/1"), maxMessageSize);
        assertEquals(connection.getHost(), "localhost");
        assertEquals(connection.getPort(), 1234);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendReceive() throws Exception {
        udpConnection.setMaxSize(1024);

        EventBuilder eventBuilder = new EventBuilder()
                .withLevel(Event.Level.DEBUG)
                .withChecksum("checksum");


        final Event toSend = eventBuilder.build();
        udpConnection.send(toSend);

        new Verifications() {{
            DatagramPacket datagramPacket;
            udpConnection.sendPacket(datagramPacket = withCapture());
            assertThat("Should receive a packet", datagramPacket != null);
            assert datagramPacket != null;
            ChunkedSentryEvent chunkedSentryEvent = assertChunkIsValid(datagramPacket, 1024, toSend.getId().toString(), 1, 1);
            LinkedHashMap<String, Object> event = (LinkedHashMap<String, Object>) new ObjectMapper().readValue(chunkedSentryEvent.chunk, Object.class);
            assertEquals(event.get("checksum"), "checksum", "Checksum does not match");
            assertEquals(event.get("level"), "debug", "Level does not match");
        }};
    }

    @Test
    public void testChunkification() throws Exception {
        udpConnection.setMaxSize(100);

        EventBuilder eventBuilder = new EventBuilder()
                .withLevel(Event.Level.DEBUG)
                .withChecksum("checksum");


        final Event toSend = eventBuilder.build();
        udpConnection.send(toSend);

        new Verifications(4) {{
            List<DatagramPacket> dataObjects = new ArrayList<>();
            udpConnection.sendPacket(withCapture(dataObjects));

            assertChunkIsValid(dataObjects.get(0), 100, toSend.getId().toString(), 1, dataObjects.size());
            assertChunkIsValid(dataObjects.get(1), 100, toSend.getId().toString(), 2, dataObjects.size());
            assertChunkIsValid(dataObjects.get(2), 100, toSend.getId().toString(), 3, dataObjects.size());
            assertChunkIsValid(dataObjects.get(3), 100, toSend.getId().toString(), 4, dataObjects.size());
        }};
    }


    private ChunkedSentryEvent assertChunkIsValid(DatagramPacket datagramPacket, int expectedSize, String eventId, int numChunk, int total) throws IOException {
        assertThat("Should receive a packet", datagramPacket != null);
        ObjectMapper om = new ObjectMapper();
        ChunkedSentryEvent chunkedSentryEvent = om.readValue(datagramPacket.getData(), ChunkedSentryEvent.class);
        assertEquals(chunkedSentryEvent.id, eventId, "Event Id should match");
        assertEquals(chunkedSentryEvent.numChunk, numChunk, "Chunk num is invalid");
        assertEquals(chunkedSentryEvent.total, total, "Total chunks is invalid");
        assertThat("Size should be less than maxSize", chunkedSentryEvent.chunk.length() <= expectedSize);
        return chunkedSentryEvent;
    }

    private static class ChunkedSentryEvent {
        private String id;
        private int numChunk;
        private int total;
        private String chunk;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getNumChunk() {
            return numChunk;
        }

        public void setNumChunk(int numChunk) {
            this.numChunk = numChunk;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public String getChunk() {
            return chunk;
        }

        public void setChunk(String chunk) {
            this.chunk = chunk;
        }
    }

}
