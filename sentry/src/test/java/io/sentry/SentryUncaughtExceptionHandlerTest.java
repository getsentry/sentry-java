package io.sentry;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SentryUncaughtExceptionHandlerTest {
    private Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    @BeforeMethod
    public void setup() {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @AfterMethod
    public void teardown() {
        Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler);
    }

    @Test
    public void testUnwrapped() {
        Thread.setDefaultUncaughtExceptionHandler(null);

        SentryUncaughtExceptionHandler handler = SentryUncaughtExceptionHandler.setup();
        assertThat(handler, is(sameInstance(Thread.getDefaultUncaughtExceptionHandler())));

        handler.disable();
        assertThat(Thread.getDefaultUncaughtExceptionHandler(), is(nullValue()));
        assertThat(handler.isEnabled(), is(false));
    }

    @Test
    public void testWrapped() {
        Thread.setDefaultUncaughtExceptionHandler(null);

        final SentryUncaughtExceptionHandler handler = SentryUncaughtExceptionHandler.setup();
        assertThat(handler, is(sameInstance(Thread.getDefaultUncaughtExceptionHandler())));

        Thread.UncaughtExceptionHandler wrappingHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                handler.uncaughtException(t, e);
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(wrappingHandler);

        handler.disable();
        assertThat(handler, is(not(Thread.getDefaultUncaughtExceptionHandler())));
        assertThat(handler.isEnabled(), is(false));
    }
}
