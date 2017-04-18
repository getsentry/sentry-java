package io.sentry.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

public class SentryITActivity extends Activity {

    private AtomicBoolean customFactoryUsed = new AtomicBoolean(false);

    class CustomAndroidSentryFactory extends AndroidSentryFactory {
        /**
         * Construct an AndroidSentryFactory using the specified Android Context.
         *
         * @param ctx Android Context.
         */
        public CustomAndroidSentryFactory(Context ctx) {
            super(ctx);
            customFactoryUsed.set(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sentry.init(
            this.getApplicationContext(),
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1",
            new CustomAndroidSentryFactory(getApplicationContext()));
    }

    public void sendEvent() {
        Sentry.capture("sendEvent()");
    }

    public boolean getCustomFactoryUsed() {
        return customFactoryUsed.get();
    }

}
