package net.kencochrane.raven;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper providing asynchronous support for any other concrete transport layer.
 */
public abstract class AsyncTransport extends Transport {

    private static final Logger LOG = Logger.getLogger("raven.client");

    public interface Option {
        String WAIT_WHEN_FULL = "raven.waitWhenFull";
        boolean WAIT_WHEN_FULL_DEFAULT = true;
        String CAPACITY = "raven.capacity";
    }

    public final Transport transport;
    protected final BlockingQueue<Message> queue;
    protected final Thread workerThread;

    public AsyncTransport(Transport transport, BlockingQueue<Message> queue) {
        super(transport.dsn);
        this.transport = transport;
        this.queue = queue;
        workerThread = new Thread(new Worker(this));
    }

    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void start() {
        workerThread.setDaemon(true);
        workerThread.setName("Raven-" + workerThread.getName());
        workerThread.start();
        super.start();
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        super.stop();
        workerThread.interrupt();
        try {
            workerThread.join(3000);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, e.getMessage(), e);
        }
    }

    public static AsyncTransport build(Transport transport) {
        int capacity = transport.dsn.getOptionAsInt(Option.CAPACITY, -1);
        BlockingQueue<Message> queue = null;
        if (capacity < 0) {
            queue = new LinkedBlockingDeque<Message>();
        } else {
            queue = new LinkedBlockingDeque<Message>(capacity);
        }
        boolean waitWhenFull = transport.dsn.getOptionAsBoolean(Option.WAIT_WHEN_FULL, Option.WAIT_WHEN_FULL_DEFAULT);
        if (waitWhenFull) {
            return new WaitingAsyncTransport(transport, queue);
        }
        return new LossyAsyncTransport(transport, queue);
    }

    /**
     * Asynchronous transport layer that will wait for space to come available when the underlying queue has reached its
     * maximum capacity.
     */
    public static class WaitingAsyncTransport extends AsyncTransport {

        public WaitingAsyncTransport(Transport transport, BlockingQueue<Message> queue) {
            super(transport, queue);
        }

        @Override
        public void send(String messageBody, long timestamp) throws IOException {
            try {
                queue.put(new Message(messageBody, timestamp));
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    /**
     * Asynchronous transport layer that will drop messages when the underlying queue has reached its maximum capacity.
     */
    public static class LossyAsyncTransport extends AsyncTransport {

        public LossyAsyncTransport(Transport transport, BlockingQueue<Message> queue) {
            super(transport, queue);
        }

        @Override
        public void send(String messageBody, long timestamp) throws IOException {
            try {
                queue.add(new Message(messageBody, timestamp));
            } catch (IllegalStateException e) {
                throw new IOException(e);
            }
        }
    }

    public static class Message {
        public final String messageBody;
        public final long timestamp;

        public Message(String messageBody, long timestamp) {
            this.messageBody = messageBody;
            this.timestamp = timestamp;
        }
    }

    public class Worker implements Runnable {

        public final AsyncTransport transport;

        public Worker(AsyncTransport transport) {
            this.transport = transport;
        }

        @Override
        public void run() {
            while (transport.isStarted()) {
                try {
                    Message m = transport.queue.take();
                    transport.transport.send(m.messageBody, m.timestamp);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, e.getMessage(), e);
                } catch (InterruptedException e) {
                    break;
                }
            }
            // Try to get the remaining message sent
            for (Message m : transport.queue) {
                try {
                    transport.transport.send(m.messageBody, m.timestamp);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }
    }

}
