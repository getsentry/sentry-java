package net.kencochrane.raven.event.interfaces;


import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StackTraceInterfaceTest {
    @Test
    public void testCalculationCommonStackFrames() {
        Exception exception = new RuntimeException("exception1");
        exception = new RuntimeException("exception2", exception);

        StackTraceInterface stackTraceInterface = new StackTraceInterface(exception.getCause().getStackTrace(),
                exception.getStackTrace());

        assertThat(stackTraceInterface.getFramesCommonWithEnclosing(), is(exception.getStackTrace().length - 1));
    }
}
