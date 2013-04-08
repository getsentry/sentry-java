package net.kencochrane.raven.marshaller.simplejson;

import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SimpleJsonStackTraceInterfaceMarshaller implements SimpleJsonInterfaceMarshaller {
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
    //TODO: add a way to add content here.
    private Set<String> notInAppFrames = new HashSet<String>();

    //TODO: Remove this attempt to set a sensible default setting.
    {
        notInAppFrames.add("com.sun.");
        notInAppFrames.add("java.");
        notInAppFrames.add("javax.");
        notInAppFrames.add("org.omg.");
        notInAppFrames.add("sun.");
        notInAppFrames.add("junit.");
        notInAppFrames.add("com.intellij.rt.");
    }

    /**
     * Create a fake frame to allow chained exceptions.
     *
     * @param throwable Exception for which a fake frame should be created
     * @return a fake frame allowing to chain exceptions smoothly in Sentry.
     */
    private JSONObject createFakeFrame(ImmutableThrowable throwable) {
        JSONObject fakeFrame = new JSONObject();
        String message = "Caused by: " + throwable.getActualClass().getName();
        if (throwable.getMessage() != null)
            message += " (\"" + throwable.getMessage() + "\")";
        fakeFrame.put(MODULE_PARAMETER, message);
        fakeFrame.put(IN_APP_PARAMETER, true);
        return fakeFrame;
    }

    /**
     * Creates a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     * @return frame extracted from the stackTraceElement.
     */
    private JSONObject createFrame(StackTraceElement stackTraceElement) {
        JSONObject currentFrame = new JSONObject();
        // Do not display the file name (irrelevant) as it replaces the module in the sentry interface.
        //currentFrame.put(FILENAME_PARAMETER, stackTraceElement.getFileName());
        currentFrame.put(MODULE_PARAMETER, stackTraceElement.getClassName());
        currentFrame.put(IN_APP_PARAMETER, isFrameInApp(stackTraceElement));
        currentFrame.put(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
        currentFrame.put(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        return currentFrame;
    }

    private boolean isFrameInApp(StackTraceElement stackTraceElement) {
        //TODO: A set is absolutely not performant here, a Trie could be a better solution.
        for (String notInAppFrame : notInAppFrames) {
            if (stackTraceElement.getClassName().startsWith(notInAppFrame)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public JSONObject serialiseInterface(SentryInterface sentryInterface) {
        if (!(sentryInterface instanceof StackTraceInterface)) {
            //TODO: Do something better here!
            throw new IllegalArgumentException();
        }

        StackTraceInterface stackTraceInterface = (StackTraceInterface) sentryInterface;
        JSONObject jsonObject = new JSONObject();
        JSONArray frames = new JSONArray();
        ImmutableThrowable currentThrowable = stackTraceInterface.getThrowable();
        boolean firstFrame = true;
        while (currentThrowable != null) {
            if (firstFrame) {
                firstFrame = false;
            } else {
                frames.add(createFakeFrame(currentThrowable));
            }
            for (StackTraceElement stackTraceElement : currentThrowable.getStackTrace()) {
                frames.add(createFrame(stackTraceElement));
            }
            currentThrowable = currentThrowable.getCause();
        }
        Collections.reverse(frames);
        jsonObject.put(FRAMES_PARAMETER, frames);

        return jsonObject;
    }
}
