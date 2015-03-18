package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Binding allowing to convert a {@link StackTraceInterface} into a JSON stream.
 */
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
    private static final String LAMBDA_METHOD_NAME = "lambda$work$";
    private static final String LAMBDA_CLASS_NAME = "$$Lambda$";
    private Collection<String> notInAppFrames = Collections.emptyList();
    private boolean removeCommonFramesWithEnclosing = true;
    private boolean cleanLambdaFrames = true;

    /**
     * Writes a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     */
    private void writeFrame(JsonGenerator generator, StackTraceElement stackTraceElement, boolean commonWithEnclosing)
            throws IOException {
        generator.writeStartObject();
        // Do not display the file name (irrelevant) as it replaces the module in the sentry interface.
        //generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, cleanLambdaClassName(stackTraceElement.getClassName()));
        generator.writeBooleanField(IN_APP_PARAMETER, !(removeCommonFramesWithEnclosing && commonWithEnclosing)
                && isFrameInApp(stackTraceElement));
        generator.writeStringField(FUNCTION_PARAMETER, cleanLambdaMethodName(stackTraceElement.getMethodName()));
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        generator.writeEndObject();
    }

    private boolean isFrameInApp(StackTraceElement stackTraceElement) {
        //TODO: A set is absolutely not efficient here, a Trie could be a better solution.
        for (String notInAppFrame : notInAppFrames) {
            if (stackTraceElement.getClassName().startsWith(notInAppFrame)) {
                return false;
            }
        }
        return true;
    }

    // instead of method name "lambda$work$1", use "lambda$work$"
    private String cleanLambdaMethodName(String methodName) {
        if (!cleanLambdaFrames) {
            return methodName;
        }
        return methodName.startsWith(LAMBDA_METHOD_NAME) ? LAMBDA_METHOD_NAME : methodName;
    }

    // instead of class name "xxx.$$Lambda$30/2081171245", use "xxx.$$Lambda$"
    private String cleanLambdaClassName(String className) {
        if (!cleanLambdaFrames) {
            return className;
        }
        int idx = className.indexOf(LAMBDA_CLASS_NAME);
        if (idx >= 0) {
            return className.substring(0, idx + LAMBDA_CLASS_NAME.length());
        }
        return className;
    }

    @Override
    public void writeInterface(JsonGenerator generator, StackTraceInterface stackTraceInterface) throws IOException {
        StackTraceElement[] stackTrace = stackTraceInterface.getStackTrace();

        generator.writeStartObject();
        generator.writeArrayFieldStart(FRAMES_PARAMETER);
        int commonWithEnclosing = stackTraceInterface.getFramesCommonWithEnclosing();

        // Go through the stackTrace frames from the first call to the last
        for (int i = stackTrace.length - 1; i >= 0; i--) {
            writeFrame(generator, stackTrace[i], commonWithEnclosing-- > 0);
        }

        generator.writeEndArray();
        generator.writeEndObject();
    }

    public void setRemoveCommonFramesWithEnclosing(boolean removeCommonFramesWithEnclosing) {
        this.removeCommonFramesWithEnclosing = removeCommonFramesWithEnclosing;
    }

    public void setCleanLambdaFrames(boolean cleanLambdaFrames) {
        this.cleanLambdaFrames = cleanLambdaFrames;
    }

    public void setNotInAppFrames(Collection<String> notInAppFrames) {
        this.notInAppFrames = notInAppFrames;
    }
}
