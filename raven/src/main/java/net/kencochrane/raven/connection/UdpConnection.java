package net.kencochrane.raven.connection;

import net.kencochrane.raven.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import net.kencochrane.raven.marshaller.json.JsonMarshaller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection to a Sentry server through an UDP connection.
 */
public class UdpConnection extends AbstractConnection {
    private static final Logger logger = Logger.getLogger(UdpConnection.class.getCanonicalName());
    private static final int DEFAULT_UDP_PORT = 9001;
    private DatagramSocket socket;
    private Charset charset = Charset.defaultCharset();
    private Marshaller marshaller = new JsonMarshaller();

    public UdpConnection(Dsn dsn) {
        super(dsn);
        openSocket(dsn.getHost(), dsn.getPort());
    }

    public UdpConnection(String hostname, String publicKey, String secretKey) {
        this(hostname, DEFAULT_UDP_PORT, publicKey, secretKey);
    }

    public UdpConnection(String hostname, int port, String publicKey, String secretKey) {
        super(publicKey, secretKey);
        openSocket(hostname, port);
    }

    @Override
    public void send(Event event) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeHeader(baos);
            marshaller.marshall(event, baos);

            byte[] message = baos.toByteArray();
            DatagramPacket packet = new DatagramPacket(message, message.length);
            socket.send(packet);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "An exception occurred while trying to establish a connection to the sentry server");
        }
    }

    private void writeHeader(OutputStream os) throws IOException {
        os.write(getAuthHeader().getBytes(charset));
        os.write("\n\n".getBytes(charset));
    }

    private void openSocket(String hostname, int port) {
        try {
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(hostname, port));
        } catch (SocketException e) {
            throw new IllegalStateException("The UDP connection couldn't be used, impossible to send anything "
                    + "to sentry", e);
        }
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
