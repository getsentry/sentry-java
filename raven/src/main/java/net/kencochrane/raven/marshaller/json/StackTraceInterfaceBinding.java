package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class StackTraceInterfaceBinding implements InterfaceBinding<StackTraceInterface> {
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
    private final Set<String> notInAppFrames;

    public StackTraceInterfaceBinding() {
        notInAppFrames = new HashSet<String>();
        notInAppFrames.add("com.sun.");
        notInAppFrames.add("java.");
        notInAppFrames.add("javax.");
        notInAppFrames.add("org.omg.");
        notInAppFrames.add("sun.");
        notInAppFrames.add("junit.");
        notInAppFrames.add("com.intellij.rt.");
    }

    public StackTraceInterfaceBinding(Set<String> notInAppFrames) {
        // Makes a copy to avoid an external modification.
        this.notInAppFrames = new HashSet<String>(notInAppFrames);
    }

    /**
     * Writes a fake frame to allow chained exceptions.
     *
     * @param throwable Exception for which a fake frame should be created
     */
    private void writeFakeFrame(JsonGenerator generator, ImmutableThrowable throwable) throws IOException {
        String message = "Caused by: " + throwable.getActualClass().getName();
        if (throwable.getMessage() != null)
            message += " (\"" + throwable.getMessage() + "\")";

        generator.writeStartObject();
        generator.writeStringField(MODULE_PARAMETER, message);
        generator.writeBooleanField(IN_APP_PARAMETER, true);
        generator.writeEndObject();
    }

    /**
     * Writes a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     */
    private void writeFrame(JsonGenerator generator, StackTraceElement stackTraceElement) throws IOException {
        generator.writeStartObject();
        // Do not display the file name (irrelevant) as it replaces the module in the sentry interface.
        //generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, stackTraceElement.getClassName());
        generator.writeBooleanField(IN_APP_PARAMETER, isFrameInApp(stackTraceElement));
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        generator.writeEndObject();
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
    public void writeInterface(JsonGenerator generator, StackTraceInterface stackTraceInterface) throws IOException {
        ImmutableThrowable currentThrowable = stackTraceInterface.getThrowable();
        Deque<ImmutableThrowable> throwableStack = new LinkedList<ImmutableThrowable>();

        //Inverse the chain of exceptions to get the first exception thrown first.
        while (currentThrowable != null) {
            throwableStack.push(currentThrowable);
            currentThrowable = currentThrowable.getCause();
        }

        generator.writeStartObject();
        generator.writeArrayFieldStart(FRAMES_PARAMETER);
        while (!throwableStack.isEmpty()) {
            currentThrowable = throwableStack.pop();
            StackTraceElement[] stackFrames = currentThrowable.getStackTrace();

            // Go through the stackTrace frames from the first call to the last
            for (int i = currentThrowable.getStackTrace().length - 1; i >= 0; i--) {
                writeFrame(generator, stackFrames[i]);
            }

            if (!throwableStack.isEmpty())
                writeFakeFrame(generator, currentThrowable);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }
}
