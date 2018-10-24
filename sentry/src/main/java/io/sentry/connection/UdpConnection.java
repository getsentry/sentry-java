package io.sentry.connection;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import io.sentry.marshaller.json.JsonMarshaller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Support for sending events using UDP transport.
 */
public class UdpConnection implements Connection {

    /**
     * Host to send UDP data to.
     */
    private final String host;
    /**
     * UDP Port.
     */
    private final int port;
    /**
     * Marshaller to generate JSON payload for Sentry Events.
     */
    private final JsonMarshaller marshaller;
    /**
     * Set of callbacks that get called on send sucess or failure of event send.
     */
    private final Set<EventSendCallback> eventSendCallbacks;
    /**
     * JsonFactory for creating chunks.
     */
    private final JsonFactory jsonFactory;
    /**
     * The maximum chunk size beyond which a new UDP message will be sent.
     */
    private int maxChunkSize;
    /**
     * InetAddress for the remote UDP socket.
     */
    private InetAddress address = null;
    /**
     * UDP Socket.
     */
    private DatagramSocket socket = null;


    /**
     * Create a UDP connection.
     * @param dsn for example. udp://public:private@localhost:1234/1.
     * @param maxChunkSize maxChunkSize.
     */
    public UdpConnection(Dsn dsn, int maxChunkSize) {
        this.host = dsn.getHost();
        this.port = dsn.getPort();
        this.maxChunkSize = maxChunkSize;
        this.jsonFactory = new JsonFactory();
        this.marshaller = new JsonMarshaller();
        this.marshaller.setCompression(false);
        this.eventSendCallbacks = new HashSet<>();
    }


    private void init() throws UnknownHostException, SocketException {
        if (address == null) {
            address = InetAddress.getByName(this.host);
        }
        if (socket == null) {
            socket = new DatagramSocket();
        }
    }

    @Override
    public void send(Event event) throws ConnectionException {
        try {

            this.init();

            ByteArrayOutputStream destination = new ByteArrayOutputStream();
            marshaller.marshall(event, destination);
            byte[] eventBuf = destination.toByteArray();

            List<String> chunks = new ArrayList<>();
            chunkify(new String(eventBuf), chunks);
            int totalChunks = chunks.size();
            for (int numChunk = 0; numChunk < totalChunks; numChunk++) {
                String chunk = chunks.get(numChunk);
                String eventId = event.getId().toString();
                byte[] sendArray = this.getChunkBytes(chunk, eventId, numChunk + 1, totalChunks);
                DatagramPacket packet = new DatagramPacket(sendArray, sendArray.length, address, this.port);
                this.sendPacket(packet);
            }

            for (EventSendCallback eventSendCallback : eventSendCallbacks) {
                eventSendCallback.onSuccess(event);
            }

        } catch (IOException ioex) {
            for (EventSendCallback eventSendCallback : eventSendCallbacks) {
                eventSendCallback.onFailure(event, ioex);
            }
            throw new ConnectionException("Error writing to udp", ioex);
        }
    }

    /**
     * Package private method to allow mocking of socket send.
     * @param packet
     *
     * @throws IOException
     * @return void.
     */
    void sendPacket(DatagramPacket packet) throws IOException {
        this.socket.send(packet);
    }

    /**
     * Package private method to allow unit tests to set maxChunkSize.
     * @param maxChunkSize
     *
     * @return void.
     */
    void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    private void chunkify(String payload, List<String> chunks) {
        int payloadLength = payload.length();
        if (payloadLength <= this.maxChunkSize) {
            chunks.add(payload);
        } else {
            int split = payloadLength / 2;
            String firstHalf = payload.substring(0, split);
            String secondHalf = payload.substring(split);
            chunkify(firstHalf, chunks);
            chunkify(secondHalf, chunks);
        }
    }

    private byte[] getChunkBytes(String eventBuff, String eventId, int numChunk, int total) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator generator = this.jsonFactory.createGenerator(baos);
        generator.writeStartObject();
        generator.writeStringField("id", eventId);
        generator.writeNumberField("numChunk", numChunk);
        generator.writeNumberField("total", total);
        generator.writeStringField("chunk", eventBuff);
        generator.writeEndObject();
        generator.flush();
        return baos.toByteArray();
    }

    /**
     * Get host parsed from dsn.
     * @return hostName.
     */
    public String getHost() {
        return host;
    }

    /**
     * Get port parsed from dsn.
     * @return port.
     */
    public int getPort() {
        return port;
    }


    @Override
    public void addEventSendCallback(EventSendCallback eventSendCallback) {
        eventSendCallbacks.add(eventSendCallback);
    }


    @Override
    public void close() throws IOException {
        if (socket != null) {
            this.socket.close();
        }
    }

}
