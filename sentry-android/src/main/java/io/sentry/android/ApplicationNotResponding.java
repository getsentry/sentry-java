// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRError.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android;

import android.os.Looper;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.lang.Thread.State;

/**
 * Error thrown by ANRWatchDog when an ANR is detected.
 * Contains the stack trace of the frozen UI thread.
 * <p>
 * It is important to notice that, in an ApplicationNotResponding, all the "Caused by" are not really the cause
 * of the exception. Each "Caused by" is the stack trace of a running thread. Note that the main
 * thread always comes first.
 */
class ApplicationNotResponding extends Throwable {

    private Thread.State state;

    public ApplicationNotResponding(String message) {
        super(message);
    }

    public Thread.State getState() {
        return state;
    }

    @Override
    public Throwable fillInStackTrace() {
        final Thread mainThread = Looper.getMainLooper().getThread();
        state = mainThread.getState();
        setStackTrace(mainThread.getStackTrace());
        return this;
    }
}