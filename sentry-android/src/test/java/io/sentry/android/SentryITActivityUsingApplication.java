package io.sentry.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;

import java.util.concurrent.atomic.AtomicBoolean;

public class SentryITActivityUsingApplication extends Activity {

    private AtomicBoolean customFactoryUsed = new AtomicBoolean(false);

    class CustomAndroidSentryClientFactory extends AndroidSentryClientFactory {

        /**
         * Construct an AndroidSentryClientFactory using the specified Android Context.
         *
         * @param app Android Application
         */
        CustomAndroidSentryClientFactory(Application app, Lookup lookup) {
            super(app, lookup);
            customFactoryUsed.set(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sentry.init(new SentryOptions(Lookup.getDefault(),
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1",
            new CustomAndroidSentryClientFactory(getApplication(), Lookup.getDefault())));
    }

    public void sendEvent() {
        Sentry.capture("sendEvent()");
    }

    public boolean getCustomFactoryUsed() {
        return customFactoryUsed.get();
    }
}
