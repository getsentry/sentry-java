package net.kencochrane.raven.sentrystub;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebListener
public class SentryUdpContextListener implements ServletContextListener {
    private static final int DEFAULT_SENTRY_UDP_PORT = 9001;
    private static final String SENTRY_UDP_PORT_PARAMETER = "sentryUdpPort";
    private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private DatagramSocket udpSocket;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String sentryUdpPortParameter = sce.getServletContext().getInitParameter(SENTRY_UDP_PORT_PARAMETER);
        int port = DEFAULT_SENTRY_UDP_PORT;
        if (sentryUdpPortParameter != null)
            port = Integer.parseInt(sentryUdpPortParameter);
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

    private static class UdpRequestHandler implements Runnable {
        private final DatagramPacket datagramPacket;

        private UdpRequestHandler(DatagramPacket datagramPacket) {
            this.datagramPacket = datagramPacket;
        }

        @Override
        public void run() {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(datagramPacket.getData(),
                        datagramPacket.getOffset(),
                        datagramPacket.getLength());
                //TODO extract the AUTH header validate it, send the rest to the JSon validator
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    private class UdpListenerThread extends Thread {
        @Override
        public void run() {
            while (!udpSocket.isClosed()) {
                try {
                    // We'll assume that no-one sends a > 65KB datagram (max size allowed on IPV4).
                    byte[] buffer = new byte[1 << 16];
                    DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(datagramPacket);
                } catch (Exception e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
    }
}
