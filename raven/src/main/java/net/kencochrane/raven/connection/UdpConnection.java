package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Connection to a Sentry server through an UDP connection.
 */
public class UdpConnection extends AbstractConnection {
    /**
     * Default UDP port for a Sentry instance.
     */
    public static final int DEFAULT_UDP_PORT = 9001;
    private DatagramSocket socket;
    private Marshaller marshaller;

    /**
     * Creates an UDP connection to a Sentry server.
     *
     * @param hostname  hostname of the Sentry server.
     * @param publicKey public key of the current project.
     * @param secretKey private key of the current project.
     */
    public UdpConnection(String hostname, String publicKey, String secretKey) {
        this(hostname, DEFAULT_UDP_PORT, publicKey, secretKey);
    }

    /**
     * Creates an UDP connection to a Sentry server.
     *
     * @param hostname  hostname of the Sentry server.
     * @param port      Port on which the Sentry server listens.
     * @param publicKey public key of the current project.
     * @param secretKey private key of the current project.
     */
    public UdpConnection(String hostname, int port, String publicKey, String secretKey) {
        super(publicKey, secretKey);
        openSocket(hostname, port);
    }

    @Override
    protected void doSend(Event event) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream outputStream = baos) {
            writeHeader(outputStream);
            marshaller.marshall(event, outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Got an exception while marshalling a message", e);
        }
        byte[] message = baos.toByteArray();

        try {
            DatagramPacket packet = new DatagramPacket(message, message.length);
            socket.send(packet);
        } catch (IOException e) {
            throw new ConnectionException(
                    "An exception occurred while trying to establish a connection to the sentry server", e);
        }
    }

    private void writeHeader(OutputStream os) throws IOException {
        os.write(getAuthHeader().getBytes("UTF-8"));
        os.write("\n\n".getBytes("UTF-8"));
    }

    private void openSocket(String hostname, int port) {
        try {
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(hostname, port));
        } catch (SocketException e) {
            throw new ConnectionException("The UDP connection couldn't be used, impossible to send anything "
                    + "to sentry", e);
        }
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
