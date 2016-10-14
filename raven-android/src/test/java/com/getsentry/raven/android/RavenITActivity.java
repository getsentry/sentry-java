package com.getsentry.raven.android;

import android.app.Activity;
import android.os.Bundle;

public class RavenITActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Raven.init(
            this.getApplicationContext(),
            "http://8292bf61d620417282e68a72ae03154a:e3908e05ad874b24b7a168992bfa3577@localhost:8080/1");
    }

    public void sendEvent() {
        Raven.capture("sendEvent()");
    }

}
