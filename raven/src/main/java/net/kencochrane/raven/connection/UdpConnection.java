package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.LoggedEvent;

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
    private DatagramSocket socket;
    private Charset charset = Charset.defaultCharset();

    public UdpConnection(Dsn dsn) {
        super(dsn);
        openSocket();
    }

    @Override
    public void send(LoggedEvent event) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeHeader(baos);
            baos.flush();
            baos.close();

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

    private void openSocket() {
        try {
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(getDsn().getHost(), getDsn().getPort()));
        } catch (SocketException e) {
            throw new IllegalStateException("The UDP connection couldn't be used, impossible to send anything " +
                    "to sentry", e);
        }
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
