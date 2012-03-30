package net.kencochrane.sentry;

import org.apache.log4j.spi.LoggingEvent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: mphilpot
 * Date: 3/29/12
 */
public class SentryQueue
{
    private static SentryQueue ourInstance = new SentryQueue();
    private static BlockingQueue<LoggingEvent> queue;

    private SentryWorker worker;

    public static SentryQueue getInstance()
    {
        return ourInstance;
    }

    private SentryQueue()
    {
        queue = new LinkedBlockingQueue<LoggingEvent>();

        worker = null;
    }

    public void shutdown()
    {
        worker.shutdown();
        worker.interrupt();
    }

    public void addEvent(LoggingEvent le, String sentryDSN, String proxy)
    {
        // There might be a better way to do this before, rather than
        // always sending the sentry connection information
        synchronized (this)
        {
            if(worker == null)
            {
                worker = new SentryWorker(queue, sentryDSN, proxy);
                worker.start();
            }
        }

        queue.add(le);
    }
}
