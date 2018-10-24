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

public class UdpConnection implements Connection {

    private final String host;
    private final int port;


    private final JsonMarshaller marshaller;
    private final Set<EventSendCallback> eventSendCallbacks;
    private final JsonFactory jsonFactory;

    private int maxSize;
    private InetAddress address = null;
    private DatagramSocket socket = null;


    public UdpConnection(Dsn dsn, int maxMessageSize) {
        this.host = dsn.getHost();
        this.port = dsn.getPort();
        this.maxSize = maxMessageSize;
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
                byte[] sendArray = this.getChunkBytes(chunks.get(numChunk), event.getId().toString(), numChunk + 1, totalChunks);
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

    void sendPacket(DatagramPacket packet) throws IOException {
        this.socket.send(packet);
    }

    private void chunkify(String payload, List<String> chunks) {
        int payloadLength = payload.length();
        if (payloadLength <= this.maxSize) {
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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
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
