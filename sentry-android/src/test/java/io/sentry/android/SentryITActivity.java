package io.sentry.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import io.sentry.Sentry;

import java.util.concurrent.atomic.AtomicBoolean;

public class SentryITActivity extends Activity {

    private AtomicBoolean customFactoryUsed = new AtomicBoolean(false);

    class CustomAndroidSentryClientFactory extends AndroidSentryClientFactory {
        /**
         * Construct an AndroidSentryClientFactory using the specified Android Context.
         *
         * @param ctx Android Context.
         */
        public CustomAndroidSentryClientFactory(Context ctx) {
            super(ctx);
            customFactoryUsed.set(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sentry.init(
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1",
            new CustomAndroidSentryClientFactory(getApplicationContext()));
    }

    public void sendEvent() {
        Sentry.capture("sendEvent()");
    }

    public boolean getCustomFactoryUsed() {
        return customFactoryUsed.get();
    }

}
