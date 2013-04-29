package net.kencochrane.raven.sentrystub;

import net.kencochrane.raven.sentrystub.auth.InvalidAuthException;
import net.kencochrane.raven.sentrystub.event.Event;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ContextListener stating an UDP socket when the servlet container starts.
 * <p>
 * This listener allows a {@link DatagramSocket} to be started to listen to UDP requests.
 * </p>
 */
@WebListener
public class SentryUdpContextListener implements ServletContextListener {
    private static final Logger logger = Logger.getLogger(SentryUdpContextListener.class.getCanonicalName());
    private static final int DEFAULT_SENTRY_UDP_PORT = 9001;
    private static final String SENTRY_UDP_PORT_PARAMETER = "sentryUdpPort";
    private final SentryStub sentryStub = SentryStub.getInstance();
    private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private DatagramSocket udpSocket;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String sentryUdpPortParameter = sce.getServletContext().getInitParameter(SENTRY_UDP_PORT_PARAMETER);
        startUdpSocket(sentryUdpPortParameter != null
                ? Integer.parseInt(sentryUdpPortParameter)
                : DEFAULT_SENTRY_UDP_PORT);
    }

    private void startUdpSocket(int port) {
        try {
            udpSocket = new DatagramSocket(port);
            new UdpListenerThread().start();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        executorService.shutdownNow();
        udpSocket.close();
    }

    private final class UdpRequestHandler implements Runnable {
        private final DatagramPacket datagramPacket;

        private UdpRequestHandler(DatagramPacket datagramPacket) {
            this.datagramPacket = datagramPacket;
        }

        @Override
        public void run() {
            InputStream bais = new ByteArrayInputStream(datagramPacket.getData(),
                    datagramPacket.getOffset(),
                    datagramPacket.getLength());
            validateAuthHeader(bais);
            Event event = sentryStub.parseEvent(bais);
            sentryStub.addEvent(event);
        }

        /**
         * Extracts the Auth Header from a binary stream and leave the stream as is once the header is parsed.
         *
         * @param inputStream
         */
        private void validateAuthHeader(InputStream inputStream) {
            try {
                Map<String, String> authHeader = parseAuthHeader(inputStream);
                sentryStub.validateAuth(authHeader);
            } catch (IOException e) {
                throw new InvalidAuthException("Impossible to extract the auth header from an UDP packet", e);
            }
        }

        private Map<String, String> parseAuthHeader(InputStream inputStream) throws IOException {
            Map<String, String> authHeader = new HashMap<String, String>();

            int i;
            StringBuilder sb = new StringBuilder();
            String key = null;
            while ((i = inputStream.read()) >= 0) {
                if (i == '\n') {
                    authHeader.put(key, sb.toString().trim());
                    //Assume it's the double \n, parse the next once
                    inputStream.read();
                    break;
                } else if (i == '=' && key == null) {
                    key = sb.toString().trim();
                    sb = new StringBuilder();
                } else if (i == ',' && key != null) {
                    authHeader.put(key, sb.toString().trim());
                    sb = new StringBuilder();
                    key = null;
                } else {
                    sb.append((char) i);
                }
            }
            return authHeader;
        }
    }

    private class UdpListenerThread extends Thread {
        @Override
        public void run() {
            // We'll assume that no-one sends a > 65KB datagram (max size allowed on IPV4).
            final int datagramPacketSize = 65536;
            while (!udpSocket.isClosed()) {
                try {
                    byte[] buffer = new byte[datagramPacketSize];
                    DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(datagramPacket);
                    executorService.execute(new UdpRequestHandler(datagramPacket));
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "An exception occurred during the reception of a UDP packet (server probably closing).", e);
                }
            }
        }
    }
}
