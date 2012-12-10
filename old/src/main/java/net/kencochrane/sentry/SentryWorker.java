package net.kencochrane.sentry;

import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.concurrent.BlockingQueue;

/**
 * User: mphilpot
 * Date: 3/29/12
 */
public class SentryWorker extends Thread
{
    private boolean shouldShutdown;

    private RavenClient client;

    private BlockingQueue<LoggingEvent> queue;

    public SentryWorker(BlockingQueue<LoggingEvent> queue, String sentryDSN, String proxy, boolean naiveSsl)
    {
        this.shouldShutdown = false;
        this.queue = queue;
        this.client = new RavenClient(sentryDSN, proxy, naiveSsl);
    }

    @Override
    public void run()
    {
        while(!shouldShutdown)
        {
            try
            {
                LoggingEvent le = queue.take();

                sendToSentry(le);
            }
            catch (InterruptedException e)
            {
                // Thread interrupted... probably shutting down
            }
        }
    }

    public void shutdown()
    {
        shouldShutdown = true;
    }

    public void sendToSentry(LoggingEvent loggingEvent)
    {
        synchronized (this)
        {
            try
            {
                // get timestamp and timestamp in correct string format.
                long timestamp = loggingEvent.getTimeStamp();

                // get the log and info about the log.
                String logMessage = loggingEvent.getRenderedMessage();
                String loggingClass = loggingEvent.getLogger().getName();
                int logLevel = (loggingEvent.getLevel().toInt() / 1000);  //Need to divide by 1000 to keep consistent with sentry
                String culprit = loggingEvent.getLoggerName();

                // is it an exception?
                ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();

                // send the message to the sentry server
                if (throwableInformation == null){
                    client.captureMessage(logMessage, timestamp, loggingClass, logLevel, culprit);
                }else{
                    client.captureException(logMessage, timestamp, loggingClass, logLevel, culprit, throwableInformation.getThrowable());
                }

            } catch (Exception e)
            {
                // Can we tell if there is another logger to send the event to?
                System.err.println(e);
            }
        }
    }

}
