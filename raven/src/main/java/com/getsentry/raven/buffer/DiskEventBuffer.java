package com.getsentry.raven.buffer;

import com.getsentry.raven.Raven;
import com.getsentry.raven.connection.AsyncConnection;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Stores {@link Event} objects to a directory on the filesystem and allows
 * them to be flushed to Sentry (and deleted) at a later time.
 */
public class DiskEventBuffer implements EventBuffer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConnection.class);

    private int maxEvents;
    private File bufferDir;

    /**
     * Construct an DiskEventBuffer which stores errors in the specified directory on disk.
     *
     * @param bufferDir File representing directory to store buffered Events in
     * @param maxEvents The maximum number of events to store offline
     */
    public DiskEventBuffer(File bufferDir, int maxEvents) {
        this.maxEvents = maxEvents;

        try {
            bufferDir.mkdirs();
            if (bufferDir.isDirectory() && bufferDir.canWrite()) {
                // store the directory for future use
                this.bufferDir = bufferDir;
            } else {
                logger.error("Could not create Raven offline event storage directory.");
            }
        } catch (Exception e) {
            logger.error("Could not create Raven offline event storage directory.", e);
        }

        if (canAccessDir()) {
            logger.debug(Integer.toString(getNumStoredEvents()) + " stored events found in '" + bufferDir + "'.");
        }
    }

    /**
     * Store a single event to the buffer directory. Java serialization is used and each
     * event is stored in a file named by its UUID.
     *
     * @param event Event to store in buffer directory
     */
    public void buffer(Event event) {
        if (!canAccessDir()) {
            logger.warn("Buffer directory does not exist, not writing Event '" + event.getId() + "'.");
            return;
        } else {
            logger.debug("Adding Event '" + event.getId() + "' to offline storage.");
        }

        if (getNumStoredEvents() >= maxEvents) {
            logger.warn("Skipping Event '" + event.getId() + "' because at least "
                + Integer.toString(maxEvents) + " events are already stored.");
            return;
        }

        String eventPath = bufferDir.getAbsolutePath() + "/" + event.getId();
        try (FileOutputStream fos = new FileOutputStream(new File(eventPath));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(event);
        } catch (Exception e) {
            logger.error("Error writing Event '" + event.getId() + "' to offline storage.", e);
        }

        logger.debug(Integer.toString(getNumStoredEvents()) + " stored events are now in '" + bufferDir + "'.");
    }

    /**
     * Attempt to flush all events from the buffer to the Sentry server. Event files are deleted
     * after they are sent.
     */
    public void flush() {
        if (!canAccessDir()) {
            logger.warn("Buffer directory does not exist, not flushing Events.");
            return;
        } else {
            logger.debug("Flushing Events from offline storage.");
        }

        String errorMsg = "Error reading Event file in buffer.";
        for (File eventFile : bufferDir.listFiles()) {
            FileInputStream fis;
            Event event;
            Object uncastedEvent = null;
            try {
                fis = new FileInputStream(new File(eventFile.getAbsolutePath()));
                ObjectInputStream ois = new ObjectInputStream(fis);
                uncastedEvent = ois.readObject();
            } catch (Exception e) {
                logger.error(errorMsg, e);
            }

            if (uncastedEvent == null) {
                continue;
            }

            try {
                event = (Event) uncastedEvent;
            } catch (Exception e) {
                logger.error("Error casting Object to Event.", e);
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
            logger.error("Error deleting stored Event file '" + eventFile.getName() + "'.");
        }
        return deleted;
    }

    private boolean canAccessDir() {
        return bufferDir != null;
    }

    private int getNumStoredEvents() {
        return bufferDir.listFiles().length;
    }
}
