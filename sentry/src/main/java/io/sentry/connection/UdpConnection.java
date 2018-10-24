package io.sentry.connection;

import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import io.sentry.marshaller.json.JsonMarshaller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class UdpConnection implements Connection {

    private final String host;
    private final int port;
    private final JsonMarshaller marshaller;
    private final Set<EventSendCallback> eventSendCallbacks;

    private InetAddress address = null;
    private DatagramSocket socket = null;


    public UdpConnection(Dsn dsn) {
        this.host = dsn.getHost();
        this.port = dsn.getPort();
        this.marshaller = new JsonMarshaller();
        this.marshaller.setCompression(false);
        this.eventSendCallbacks = new HashSet<>();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            this.socket.close();
        }
    }

    @Override
    public void send(Event event) throws ConnectionException {
        try {
            ByteArrayOutputStream destination = new ByteArrayOutputStream();
            marshaller.marshall(event, destination);
            byte[] buf = destination.toByteArray();
            if (address == null) {
                address = InetAddress.getByName(this.host);
            }
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, this.port);
            if (socket == null) {
                socket = new DatagramSocket();
            }
            socket.send(packet);
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

    @Override
    public void addEventSendCallback(EventSendCallback eventSendCallback) {
        eventSendCallbacks.add(eventSendCallback);
    }
}
