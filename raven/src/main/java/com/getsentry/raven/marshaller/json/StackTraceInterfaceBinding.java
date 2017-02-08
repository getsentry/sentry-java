package com.getsentry.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.getsentry.raven.event.interfaces.StackTraceInterface;

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
    private Collection<String> notInAppFrames = Collections.emptyList();
    private boolean removeCommonFramesWithEnclosing = true;

    /**
     * Writes a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     */
    private void writeFrame(JsonGenerator generator, StackTraceElement stackTraceElement, boolean commonWithEnclosing)
        throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, stackTraceElement.getClassName());
        generator.writeBooleanField(IN_APP_PARAMETER, !(removeCommonFramesWithEnclosing && commonWithEnclosing)
            && isFrameInApp(stackTraceElement));
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
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

    public void setNotInAppFrames(Collection<String> notInAppFrames) {
        this.notInAppFrames = notInAppFrames;
    }
}
