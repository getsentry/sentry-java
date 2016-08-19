package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;

import com.getsentry.raven.event.Event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class EventCache {

    private final static String TAG = EventCache.class.getName();
    private final static String DIR_NAME = "raven_unsent_events";
    private final static int MAX_EVENTS = 50;

    private Context context;
    private File eventDir;

    public EventCache(Context ctx) {
        this.context = ctx;

        try {
            File tmpDir = new File(ctx.getCacheDir().getAbsolutePath(), DIR_NAME);
            tmpDir.mkdirs();
            if (tmpDir.exists() && tmpDir.isDirectory()) {
                // store the directory for future use
                eventDir = tmpDir;
            } else {
                Log.e(TAG, "Could not create Raven offline event storage directory.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not create Raven offline event storage directory.", e);
        }

        flushEvents();
    }

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
            String eventPath = eventDir.getAbsolutePath() + "/" + event.getId();
            fos = context.openFileOutput(eventPath, Context.MODE_PRIVATE);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(event);
        } catch (FileNotFoundException e) {
            Log.e(TAG, errorMsg, e);
        } catch (IOException e) {
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
    }

    public void flushEvents() {
        if (!canAccessCache()) {
            Log.d(TAG, "Cache directory does not exist, not flushing Events.");
            return;
        } else {
            Log.d(TAG, "Flushing Events from offline storage.");
        }

        String errorMsg = "Error reading Event file in cache.";
        for (File eventFile : eventDir.listFiles()) {
            FileInputStream fis = null;
            Event event = null;
            try {
                fis = context.openFileInput(eventFile.getAbsolutePath());
                ObjectInputStream ois = new ObjectInputStream(fis);
                event = (Event) ois.readObject();
            } catch (FileNotFoundException e) {
                Log.e(TAG, errorMsg, e);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, errorMsg, e);
            } catch (IOException e) {
                Log.e(TAG, errorMsg, e);
            }

            if (event == null) {
                continue;
            }

            Raven.capture(event);

            boolean deleted = eventFile.delete();
            if (!deleted) {
                Log.d(TAG, "Error deleting stored Event file '" + eventFile.getName() + "'.");
            }
        }
    }

    private boolean canAccessCache() {
        return eventDir != null;
    }

    private int getNumStoredEvents() {
        return eventDir.listFiles().length;
    }
}
