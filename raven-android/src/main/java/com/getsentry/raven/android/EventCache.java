package com.getsentry.raven.android;

import android.util.Log;

import com.getsentry.raven.event.Event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Stores {@link Event} objects to a directory on the Android filesystem and allows
 * them to be flushed to Sentry (and deleted) at a later time.
 */
public class EventCache {

    private static final String TAG = EventCache.class.getName();

    private int maxEvents;
    private File cacheDir;

    /**
     * Construct an EventCache which stores errors in a subdirectory of the Android cache directory.
     *
     * @param cacheDir File representing directory to store cached Events in
     * @param maxEvents The maximum number of events to store offline
     */
    public EventCache(File cacheDir, int maxEvents) {
        this.maxEvents = maxEvents;

        try {
            cacheDir.mkdirs();
            if (cacheDir.isDirectory() && cacheDir.canWrite()) {
                // store the directory for future use
                this.cacheDir = cacheDir;
            } else {
                Log.e(TAG, "Could not create Raven offline event storage directory.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not create Raven offline event storage directory.", e);
        }

        if (canAccessCache()) {
            Log.d(TAG, Integer.toString(getNumStoredEvents()) + " stored events found in '" + cacheDir + "'.");
        }
    }

    /**
     * Store a single event to the Android cache directory. Java serialization is used and each
     * event is stored in a file named by its UUID.
     *
     * @param event Event to store in cache directory
     */
    public void storeEvent(Event event) {
        if (!canAccessCache()) {
            Log.w(TAG, "Cache directory does not exist, not writing Event '" + event.getId() + "'.");
            return;
        } else {
            Log.d(TAG, "Adding Event '" + event.getId() + "' to offline storage.");
        }

        if (getNumStoredEvents() >= maxEvents) {
            Log.w(TAG, "Skipping Event '" + event.getId() + "' because at least "
                + Integer.toString(maxEvents) + " events are already stored.");
            return;
        }

        String eventPath = cacheDir.getAbsolutePath() + "/" + event.getId();
        try (FileOutputStream fos = new FileOutputStream(new File(eventPath));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(event);
        } catch (Exception e) {
            Log.e(TAG, "Error writing Event '" + event.getId() + "' to offline storage.", e);
        }

        Log.d(TAG, Integer.toString(getNumStoredEvents()) + " stored events are now in '" + cacheDir + "'.");
    }

    /**
     * Attempt to flush all events from the cache to the Sentry server. Event files are deleted
     * after they are sent.
     */
    public void flushEvents() {
        if (!canAccessCache()) {
            Log.w(TAG, "Cache directory does not exist, not flushing Events.");
            return;
        } else {
            Log.d(TAG, "Flushing Events from offline storage.");
        }

        String errorMsg = "Error reading Event file in cache.";
        for (File eventFile : cacheDir.listFiles()) {
            FileInputStream fis;
            Event event;
            Object uncastedEvent = null;
            try {
                fis = new FileInputStream(new File(eventFile.getAbsolutePath()));
                ObjectInputStream ois = new ObjectInputStream(fis);
                uncastedEvent = ois.readObject();
            } catch (Exception e) {
                Log.e(TAG, errorMsg, e);
            }

            if (uncastedEvent == null) {
                continue;
            }

            try {
                event = (Event) uncastedEvent;
            } catch (Exception e) {
                Log.e(TAG, "Error casting Object to Event.", e);
                deleteFile(eventFile);
                continue;
            }

            Raven.capture(event);
            deleteFile(eventFile);
        }
    }

    private boolean deleteFile(File eventFile) {
        boolean deleted = eventFile.delete();
        if (!deleted) {
            Log.e(TAG, "Error deleting stored Event file '" + eventFile.getName() + "'.");
        }
        return deleted;
    }

    private boolean canAccessCache() {
        return cacheDir != null;
    }

    private int getNumStoredEvents() {
        return cacheDir.listFiles().length;
    }
}
