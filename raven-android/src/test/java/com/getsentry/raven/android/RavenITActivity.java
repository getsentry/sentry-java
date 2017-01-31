package com.getsentry.raven.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

public class RavenITActivity extends Activity {

    private AtomicBoolean customFactoryUsed = new AtomicBoolean(false);

    class CustomAndroidRavenFactory extends AndroidRavenFactory {
        /**
         * Construct an AndroidRavenFactory using the specified Android Context.
         *
         * @param ctx Android Context.
         */
        public CustomAndroidRavenFactory(Context ctx) {
            super(ctx);
            customFactoryUsed.set(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Raven.init(
            this.getApplicationContext(),
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1",
            new CustomAndroidRavenFactory(getApplicationContext()));
    }

    public void sendEvent() {
        Raven.capture("sendEvent()");
    }

    public boolean getCustomFactoryUsed() {
        return customFactoryUsed.get();
    }

}
