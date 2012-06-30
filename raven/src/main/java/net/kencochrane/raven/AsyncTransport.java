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

    public static final long WAIT_FOR_SHUTDOWN = 3000;
    private static final Logger LOG = Logger.getLogger("raven.transport");

    /**
     * Options for the async transport layer.
     */
    public interface Option {

        /**
         * Option indicating whether the client should block when the queue is full.
         */
        String WAIT_WHEN_FULL = "raven.waitWhenFull";

        /**
         * Default value for {@link #WAIT_WHEN_FULL}: no, do not block the queue.
         */
        boolean WAIT_WHEN_FULL_DEFAULT = false;

        /**
         * Option to limit the capacity of the underlying queue.
         */
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
        final String name = "Raven-" + workerThread.getName();
        LOG.log(Level.FINE, "Starting thread " + name);
        workerThread.setDaemon(true);
        workerThread.setName(name);
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
            workerThread.join(WAIT_FOR_SHUTDOWN);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public void send(String messageBody, long timestamp) throws IOException {
        throw new UnsupportedOperationException("You probably need a subclass of " + AsyncTransport.class);
    }

    /**
     * Builds an async transport wrapper for concrete transport layers.
     * <p>
     * Any other async transport wrapper that's supposed to be registered with the {@link Client} should provide a
     * public static <code>build</code> method, accepting the transport instance to wrap.
     * </p>
     * <p>
     * This method will apply the {@link Option#WAIT_WHEN_FULL} and {@link Option#CAPACITY} options specified in the
     * {@link SentryDsn} of the transport or use the default values.
     * </p>
     *
     * @param transport transport to wrap
     * @return the async transport wrapper
     */
    public static AsyncTransport build(Transport transport) {
        int capacity = transport.dsn.getOptionAsInt(Option.CAPACITY, -1);
        boolean waitWhenFull = transport.dsn.getOptionAsBoolean(Option.WAIT_WHEN_FULL, Option.WAIT_WHEN_FULL_DEFAULT);
        return build(transport, waitWhenFull, capacity);
    }

    /**
     * Convenience method for building an async transport wrapper.
     *
     * @param transport    transport to wrap
     * @param waitWhenFull whether the underlying queue should block when full
     * @param capacity     the capacity of the underlying queue - a negative value means use the maximum capacity possible
     * @return the async transport wrapper
     */
    public static AsyncTransport build(Transport transport, boolean waitWhenFull, int capacity) {
        BlockingQueue<Message> queue = null;
        if (capacity < 0) {
            queue = new LinkedBlockingDeque<Message>();
        } else {
            queue = new LinkedBlockingDeque<Message>(capacity);
        }
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
