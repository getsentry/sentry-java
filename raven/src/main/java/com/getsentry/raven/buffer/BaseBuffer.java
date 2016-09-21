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

    class Flusher implements Runnable {

        boolean isConnected;

        @Override
        public void run() {
            if (isConnected) {
                // We're connected, go crazy!
                sendEvents(Integer.MAX_VALUE);
            } else {
                // We don't know if we're connected, send a canary...
                // If this works, we'll get a discard, which will loop back
                // around and notify flusher...
                sendEvents(1);
            }
        }

        void sendEvents(int max) {
            Iterator<Event> events = getEvents();

            int sent = 0;
            while (sent <= max) {
                if (events.hasNext()) {
                    Event event = events.next();
                    Raven.capture(event);
                }
                sent += 1;
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
