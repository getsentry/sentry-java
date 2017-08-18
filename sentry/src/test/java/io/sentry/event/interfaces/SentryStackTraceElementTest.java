package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import io.sentry.jvmti.Frame;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class SentryStackTraceElementTest extends BaseTest {
    private Method method1;
    private Method method2;
    private Method method3;

    @BeforeMethod
    public void setup() {
        // setup valid Method instances (since they can't be easily constructed)
        method1();
        method2();
        method3();
    }

    @Test
    public void testMatchingCachedFrames() throws Exception {
        // 2 exception frames, method1, method2
        StackTraceElement[] stackTraceElements = new StackTraceElement[2];
        stackTraceElements[0] = new StackTraceElement("class1", method1.getName(), "file1", 1);
        stackTraceElements[1] = new StackTraceElement("class2", method2.getName(), "file2", 2);

        // 2 cached frames, method1, method2
        Frame[] cachedFrames = new Frame[2];
        Frame.LocalVariable[] locals1 = new Frame.LocalVariable[1];
        locals1[0] = new Frame.LocalVariable("var1", "val1");
        cachedFrames[0] = new Frame(method1, locals1);

        Frame.LocalVariable[] locals2 = new Frame.LocalVariable[1];
        locals2[0] = new Frame.LocalVariable("var2", "val2");
        cachedFrames[1] = new Frame(method2, locals2);

        // convert
        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, cachedFrames);

        // assert
        assertThat(sentryStackTraceElements.length, is(2));
        assertThat((String) sentryStackTraceElements[0].getLocals().get("var1"), is("val1"));
        assertThat((String) sentryStackTraceElements[1].getLocals().get("var2"), is("val2"));
    }

    @Test
    public void testCachedFramesTooLong() throws Exception {
        // 2 exception frames, method1, method2
        StackTraceElement[] stackTraceElements = new StackTraceElement[2];
        stackTraceElements[0] = new StackTraceElement("class1", method1.getName(), "file1", 1);
        stackTraceElements[1] = new StackTraceElement("class2", method2.getName(), "file2", 2);

        // 3 cached frames, method1, method2, method3
        Frame[] cachedFrames = new Frame[3];
        Frame.LocalVariable[] locals1 = new Frame.LocalVariable[1];
        locals1[0] = new Frame.LocalVariable("var1", "val1");
        cachedFrames[0] = new Frame(method1, locals1);

        Frame.LocalVariable[] locals2 = new Frame.LocalVariable[1];
        locals2[0] = new Frame.LocalVariable("var2", "val2");
        cachedFrames[1] = new Frame(method2, locals2);

        Frame.LocalVariable[] locals3 = new Frame.LocalVariable[1];
        locals3[0] = new Frame.LocalVariable("var3", "val3");
        cachedFrames[2] = new Frame(method3, locals3);

        // convert
        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, cachedFrames);

        // assert
        assertThat(sentryStackTraceElements.length, is(2));
        assertThat((String) sentryStackTraceElements[0].getLocals().get("var1"), is("val1"));
        assertThat((String) sentryStackTraceElements[1].getLocals().get("var2"), is("val2"));
    }

    @Test
    public void testCachedFramesPartialMissing() throws Exception {
        // 3 exception frames, method1, method2, method
        StackTraceElement[] stackTraceElements = new StackTraceElement[3];
        stackTraceElements[0] = new StackTraceElement("class1", method1.getName(), "file1", 1);
        stackTraceElements[1] = new StackTraceElement("class2", method2.getName(), "file2", 2);
        stackTraceElements[2] = new StackTraceElement("class3", method3.getName(), "file3", 3);

        // 2 cached frames, method1, method3
        Frame[] cachedFrames = new Frame[2];
        Frame.LocalVariable[] locals1 = new Frame.LocalVariable[1];
        locals1[0] = new Frame.LocalVariable("var1", "val1");
        cachedFrames[0] = new Frame(method1, locals1);

        Frame.LocalVariable[] locals3 = new Frame.LocalVariable[1];
        locals3[0] = new Frame.LocalVariable("var3", "val3");
        cachedFrames[1] = new Frame(method3, locals3);

        // convert
        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, cachedFrames);

        // assert
        assertThat(sentryStackTraceElements.length, is(3));
        assertThat((String) sentryStackTraceElements[0].getLocals().get("var1"), is("val1"));
        assertThat(sentryStackTraceElements[1].getLocals(), is(nullValue()));
        assertThat(sentryStackTraceElements[2].getLocals(), is(nullValue()));
    }

    @Test
    public void testCachedFramesTooShort() throws Exception {
        // 3 exception frames, method1, method2, method
        StackTraceElement[] stackTraceElements = new StackTraceElement[3];
        stackTraceElements[0] = new StackTraceElement("class1", method1.getName(), "file1", 1);
        stackTraceElements[1] = new StackTraceElement("class2", method2.getName(), "file2", 2);
        stackTraceElements[2] = new StackTraceElement("class3", method3.getName(), "file3", 3);

        // 2 cached frames, method1, method3
        Frame[] cachedFrames = new Frame[2];
        Frame.LocalVariable[] locals1 = new Frame.LocalVariable[1];
        locals1[0] = new Frame.LocalVariable("var1", "val1");
        cachedFrames[0] = new Frame(method1, locals1);

        Frame.LocalVariable[] locals2 = new Frame.LocalVariable[1];
        locals2[0] = new Frame.LocalVariable("var2", "val2");
        cachedFrames[1] = new Frame(method2, locals2);

        // convert
        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, cachedFrames);

        // assert
        assertThat(sentryStackTraceElements.length, is(3));
        assertThat((String) sentryStackTraceElements[0].getLocals().get("var1"), is("val1"));
        assertThat((String) sentryStackTraceElements[1].getLocals().get("var2"), is("val2"));
        assertThat(sentryStackTraceElements[2].getLocals(), is(nullValue()));
    }

    @Test
    public void testNullCachedFrames() throws Exception {
        StackTraceElement[] stackTraceElements = new StackTraceElement[1];
        stackTraceElements[0] = new StackTraceElement("class", "method", "file", 1);


        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, null);

        assertThat(sentryStackTraceElements.length, is(1));
        assertThat(sentryStackTraceElements[0].getLocals(), is(nullValue()));
    }


    @Test
    public void testEmptyCachedFrames() throws Exception {
        StackTraceElement[] stackTraceElements = new StackTraceElement[1];
        stackTraceElements[0] = new StackTraceElement("class", "method", "file", 1);

        Frame[] cachedFrames = new Frame[0];

        SentryStackTraceElement[] sentryStackTraceElements = SentryStackTraceElement.fromStackTraceElements(
            stackTraceElements, cachedFrames);

        assertThat(sentryStackTraceElements.length, is(1));
        assertThat(sentryStackTraceElements[0].getLocals(), is(nullValue()));
    }

    public void method1() {
        method1 = new Object() {}.getClass().getEnclosingMethod();
    }

    public void method2() {
        method2 = new Object() {}.getClass().getEnclosingMethod();
    }

    public void method3() {
        method3 = new Object() {}.getClass().getEnclosingMethod();
    }

}
