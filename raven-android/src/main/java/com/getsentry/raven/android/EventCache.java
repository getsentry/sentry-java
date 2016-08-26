package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;

import com.getsentry.raven.event.Event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Stores {@link Event} objects to a directory on the Android filesystem and allows
 * them to be flushed to Sentry (and deleted) at a later time.
 */
public class EventCache {

    private static final String TAG = EventCache.class.getName();
    private static final String DIR_NAME = "raven_unsent_events";
    private static final int MAX_EVENTS = 50;

    private File cacheDir;

    /**
     * Construct an EventCache which stores errors in a subdirectory of the Android cache directory.
     *
     * @param ctx Android application context
     */
    public EventCache(Context ctx) {
        this.cacheDir = initEventDir(ctx);

        if (canAccessCache()) {
            Log.d(TAG, Integer.toString(getNumStoredEvents()) + " stored events found in '" + DIR_NAME + "'.");
        }
    }

    private File initEventDir(Context ctx) {
        try {
            File tmpDir = new File(ctx.getCacheDir().getAbsolutePath(), DIR_NAME);
            tmpDir.mkdirs();
            if (tmpDir.isDirectory() && tmpDir.canWrite()) {
                // store the directory for future use
                return tmpDir;
            } else {
                Log.e(TAG, "Could not create Raven offline event storage directory.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not create Raven offline event storage directory.", e);
        }

        return null;
    }

    /**
     * Store a single event to the Android cache directory. Java serialization is used and each
     * event is stored in a file named by its UUID.
     *
     * @param event Event to store in cache directory
     */
    public void storeEvent(Event event) {
        if (!canAccessCache()) {
            Log.d(TAG, "Cache directory does not exist, not writing Event '" + event.getId() + "'.");
            return;
        } else {
            Log.d(TAG, "Adding Event '" + event.getId() + "' to offline storage.");
        }

        if (getNumStoredEvents() > MAX_EVENTS) {
            Log.d(TAG, "Skipping Event '" + event.getId() + "' because at least "
                + Integer.toString(MAX_EVENTS) + " events are already stored.");
            return;
        }

        String errorMsg = "Error writing Event '" + event.getId() + "' to offline storage.";
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            String eventPath = cacheDir.getAbsolutePath() + "/" + event.getId();
            fos = new FileOutputStream(new File(eventPath));
            oos = new ObjectOutputStream(fos);
            oos.writeObject(event);
        } catch (Exception e) {
            Log.e(TAG, errorMsg, e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Log.e(TAG, errorMsg, e);
                }
            }

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, errorMsg, e);
                }
            }
        }

        Log.d(TAG, Integer.toString(getNumStoredEvents()) + " stored events are now in '" + DIR_NAME + "'.");
    }

    /**
     * Attempt to flush all events from the cache to the Sentry server. Event files are deleted
     * after they are sent.
     */
    public void flushEvents() {
        if (!canAccessCache()) {
            Log.d(TAG, "Cache directory does not exist, not flushing Events.");
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
            Log.d(TAG, "Error deleting stored Event file '" + eventFile.getName() + "'.");
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
