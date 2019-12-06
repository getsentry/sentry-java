package io.sentry.jvmti;

import io.sentry.BaseTest;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class FrameCacheTest extends BaseTest {
    @Before
    public void setup() {
        FrameCache.reset();
    }

    @Test
    public void test() throws Exception {
        FrameCache.addAppPackage("io.sentry.jvmti");

        Throwable t;

        try {
            throw new RuntimeException("foo");
        } catch (RuntimeException e) {
            t = e;

            // throwable shouldn't exist in the cache at all, so we expect true
            assertThat(FrameCache.shouldCacheThrowable(t, t.getStackTrace().length), is(true));

            FrameCache.add(t, new Frame[t.getStackTrace().length]);

            // throwable exists in the cache (with the same length), so we expect false
            assertThat(FrameCache.shouldCacheThrowable(t, t.getStackTrace().length), is(false));
        }

        assertThat(t, is(notNullValue()));

        // trim the stacktrace
        StackTraceElement[] originalStackTrace = t.getStackTrace();
        StackTraceElement[] newStackTrace = new StackTraceElement[originalStackTrace.length - 1];
        for (int i = 0; i < newStackTrace.length; i++) {
            newStackTrace[i] = originalStackTrace[i];
        }
        t.setStackTrace(newStackTrace);

        // stacktrace is smaller than existing, so we expect false
        assertThat(FrameCache.shouldCacheThrowable(t, t.getStackTrace().length), is(false));
    }

}
