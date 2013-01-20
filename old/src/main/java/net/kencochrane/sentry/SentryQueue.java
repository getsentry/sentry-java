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
    private boolean blocking;

    public static SentryQueue getInstance()
    {
        return ourInstance;
    }

    private SentryQueue()
    {
        queue = null;

        worker = null;

    }

    public void shutdown()
    {
        worker.shutdown();
        worker.interrupt();
    }

    public synchronized boolean isSetup()
    {
        return (queue != null);
    }

    public synchronized void setup(String sentryDSN, String proxy, int queueSize, boolean blocking, boolean naiveSsl)
    {
        queue = new LinkedBlockingQueue<LoggingEvent>(queueSize);
        this.blocking = blocking;

        worker = new SentryWorker(queue, sentryDSN, proxy, naiveSsl);
        worker.start();
    }

    public void addEvent(LoggingEvent le)
    {
        try
        {
            if(blocking)
            {
                queue.put(le);
            }
            else
            {
                queue.add(le);
            }
        }
        catch(IllegalStateException e)
        {
            System.err.println("Sentry Queue Full :: " + le);
        }
        catch(InterruptedException e)
        {
            System.err.println("Sentry Queue Interrupted :: "+ le);
        }
    }
}
