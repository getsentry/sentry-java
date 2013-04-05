package net.kencochrane.raven.event.interfaces;

import java.util.*;

public class StackTraceInterface implements SentryInterface {
    private static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private static final String FRAMES_PARAMETER = "frames";
    private static final String FILENAME_PARAMETER = "filename";
    private static final String FUNCTION_PARAMETER = "function";
    private static final String MODULE_PARAMETER = "module";
    private static final String LINE_NO_PARAMETER = "lineno";
    private static final String ABSOLUTE_PATH_PARAMETER = "abs_path";
    private static final String CONTEXT_LINE_PARAMETER = "context_line";
    private static final String PRE_CONTEXT_PARAMETER = "pre_context";
    private static final String POST_CONTEXT_PARAMETER = "post_context";
    private static final String IN_APP_PARAMETER = "in_app";
    private static final String VARIABLES_PARAMETER = "vars";
    private final Throwable throwable;

    public StackTraceInterface(Throwable throwable) {
        this.throwable = new ImmutableThrowable(throwable);
    }

    /**
     * Create a fake frame to allow chained exceptions.
     *
     * @param throwable Exception for which a fake frame should be created
     * @return a fake frame allowing to chain exceptions smoothly in Sentry.
     */
    private static Map<String, Object> createFakeFrame(Throwable throwable) {
        Map<String, Object> fakeFrame = new HashMap<String, Object>();
        String message = "Caused by: " + throwable.getClass().getName();
        if (throwable.getMessage() != null)
            message += " (\"" + throwable.getMessage() + "\")";
        fakeFrame.put(FILENAME_PARAMETER, message);
        fakeFrame.put(LINE_NO_PARAMETER, -1);
        return fakeFrame;
    }

    /**
     * Creates a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     * @return frame extracted from the stackTraceElement.
     */
    private static Map<String, Object> createFrame(StackTraceElement stackTraceElement) {
        Map<String, Object> currentFrame = new HashMap<String, Object>();
        currentFrame.put(FILENAME_PARAMETER, stackTraceElement.getClassName());
        currentFrame.put(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
        currentFrame.put(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        return currentFrame;
    }

    @Override
    public String getInterfaceName() {
        return STACKTRACE_INTERFACE;
    }

    @Override
    public Map<String, Object> getInterfaceContent() {
        List<Map<String, Object>> frames = new LinkedList<Map<String, Object>>();
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            frames.add(createFakeFrame(currentThrowable));
            for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
                frames.add(createFrame(stackTraceElement));
            }
            currentThrowable = currentThrowable.getCause();
        }
        return Collections.<String, Object>singletonMap(FRAMES_PARAMETER, frames);
    }
}
