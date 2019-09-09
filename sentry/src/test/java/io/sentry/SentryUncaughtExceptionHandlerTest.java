package io.sentry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SentryUncaughtExceptionHandlerTest {
    private Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    @Before
    public void setup() {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @After
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
