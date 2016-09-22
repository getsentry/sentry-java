package com.getsentry.raven.buffer;


import com.getsentry.raven.Raven;
import com.getsentry.raven.event.Event;

import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class BaseBuffer implements Buffer  {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    protected final Flusher flusher;

    protected BaseBuffer() {
        this.flusher = new Flusher();
        scheduler.scheduleAtFixedRate(flusher, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * TODO: Inner class because Flusher can only blindly call Raven.capture and needs
     * to be told by something whether the connections is up/down. The only thing that
     * really knows this is the Buffer interface, via add (down) and discard (up). If
     * not an inner class, this would be constructed by the DefaultRavenFactory and
     * both flusher and buffer would need circular references to one another regardless,
     * I believe.
     *
     * I don't know that there could/would be other more creative implementations of the
     * Flusher, so this doesn't seem like a problem to me. What DOES seem like an issue
     * is that there's no way currently for a completely external actor to say "yo, the
     * internet is up" -- which would be useful with the Android connection notification
     * stuff...
     */
    class Flusher implements Runnable {

        boolean isConnected;

        /**
         * Use the Events iterator, always try to send 1 Event (if one exists) as a canary,
         * even if we think the connection is down. Then only keep sending events if we still
         * think the connection is up.
         */
        @Override
        public void run() {
            Iterator<Event> events = getEvents();
            while (events.hasNext()) {
                Event event = events.next();
                Raven.capture(event);

                // TODO: should we sleep? configurable?
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!isConnected) {
                    return;
                }
            }
        }

        void setConnected(boolean connected) {
            isConnected = connected;
            if (connected) {
                scheduler.submit(this);
            }
        }

    }

}
