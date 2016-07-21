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
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;

public class InternalStorage {

    private final static String TAG = InternalStorage.class .getName();
    private final static String FILE_NAME = "raven.unsent_events";

    private Context context;
    private List<Event> unsentRequests;

    public InternalStorage(Context context) {
        this.context = context;
        try {
            File unsetRequestsFile = new File(context.getFilesDir(), FILE_NAME);
            if (!unsetRequestsFile.exists()) {
                writeEvents(context, new ArrayList<Event>());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.unsentRequests = this.readEvents(context);
    }

    public List<Event> getUnsentEvents() {
        final List<Event> copy = new ArrayList<>();
        synchronized (this) {
            copy.addAll(unsentRequests);
        }
        return copy;
    }

    public void addEvent(Event event) {
        synchronized(this) {
            Log.d(TAG, "adding event " + event.getId());
            if (!this.unsentRequests.contains(event)) {
                this.unsentRequests.add(event);
                this.writeEvents(context, this.unsentRequests);
            }
        }
    }

    public void removeEvent(Event event) {
        synchronized(this) {
            Log.d(TAG, "removing event " + event.getId());
            this.unsentRequests.remove(event);
            this.writeEvents(context, this.unsentRequests);
        }
    }

    private void writeEvents(Context context, List<Event> events) {
        try {
            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(events);
            oos.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Event> readEvents(Context context) {
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            List<Event> requests = (ArrayList<Event>) ois.readObject();
            ois.close();
            fis.close();
            return requests;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<Event>();
    }
}
