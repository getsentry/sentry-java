// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRWatchDog.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android;

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Salomon BRYS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * A watchdog timer thread that detects when the UI thread has frozen.
 */
@SuppressWarnings("UnusedReturnValue")
class ANRWatchDog extends Thread {

    public interface ANRListener {
        /**
         * Called when an ANR is detected.
         *
         * @param error The error describing the ANR.
         */
        void onAppNotResponding(ApplicationNotResponding error);
    }

    public interface InterruptionListener {
        void onInterrupted(InterruptedException exception);
    }

    private static final String TAG = ANRWatchDog.class.getName();

    private ANRListener _anrListener = null;
    private final Handler _uiHandler = new Handler(Looper.getMainLooper());
    private final int _timeoutInterval;

    private volatile long _tick = 0;
    private volatile boolean _reported = false;

    private final Runnable _ticker = new Runnable() {
        @Override public void run() {
            _tick = 0;
            _reported = false;
        }
    };

    /**
     * Constructs a watchdog that checks the ui thread every given interval
     *
     * @param timeoutInterval The interval, in milliseconds, between to checks of the UI thread.
     *                        It is therefore the maximum time the UI may freeze before being reported as ANR.
     * @param listener The new listener or null
     */
    public ANRWatchDog(int timeoutInterval, ANRListener listener) {
        super();
        _anrListener = listener;
        _timeoutInterval = timeoutInterval;
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    @Override
    public void run() {
        setName("|ANR-WatchDog|");

        long interval = _timeoutInterval;
        while (!isInterrupted()) {
            boolean needPost = _tick == 0;
            _tick += interval;
            if (needPost) {
                _uiHandler.post(_ticker);
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted: " + e.getMessage());
                return;
            }

            // If the main thread has not handled _ticker, it is blocked. ANR.
            if (_tick != 0 && !_reported) {
                //noinspection ConstantConditions
                if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                    Log.d(TAG, "An ANR was detected but ignored because the debugger is connected.");
                    _reported = true;
                    continue;
                }

                Log.d(TAG, "Raising ANR");
                final String message ="Application Not Responding for at least " + _timeoutInterval + " ms.";

                final ApplicationNotResponding error = new ApplicationNotResponding(message);
                _anrListener.onAppNotResponding(error);
                interval = _timeoutInterval;
                _reported = true;
            }
        }
    }
}
