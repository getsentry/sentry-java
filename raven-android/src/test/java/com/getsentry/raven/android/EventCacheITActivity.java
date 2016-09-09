package com.getsentry.raven.android;

import android.app.Activity;
import android.os.Bundle;
import com.getsentry.raven.event.Event;

import java.io.File;

public class EventCacheITActivity extends Activity {

    private EventCache eventCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Raven.init(
            this.getApplicationContext(),
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1");
    }

    public void setupEventCache(File cacheDir, int maxStoredEvents) {
        eventCache = new EventCache(cacheDir, maxStoredEvents);
    }

    public void storeEvent(Event event) {
        eventCache.storeEvent(event);
    }

    public void flushEvents() {
        eventCache.flushEvents();
    }

}
