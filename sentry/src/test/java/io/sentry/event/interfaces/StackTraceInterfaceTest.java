package io.sentry.event.interfaces;


import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StackTraceInterfaceTest {
    @Test
    public void testCalculationCommonStackFrames() throws Exception {
        Exception exception = new RuntimeException("exception1");
        exception = new RuntimeException("exception2", exception);

        StackTraceInterface stackTraceInterface = new StackTraceInterface(exception.getCause().getStackTrace(),
                exception.getStackTrace());

        assertThat(stackTraceInterface.getFramesCommonWithEnclosing(), is(exception.getStackTrace().length - 1));
    }
}
