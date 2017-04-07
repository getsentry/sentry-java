package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;

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
    private static final String COL_NO_PARAMETER = "colno";
    private static final String ABSOLUTE_PATH_PARAMETER = "abs_path";
    private static final String CONTEXT_LINE_PARAMETER = "context_line";
    private static final String PRE_CONTEXT_PARAMETER = "pre_context";
    private static final String POST_CONTEXT_PARAMETER = "post_context";
    private static final String IN_APP_PARAMETER = "in_app";
    private static final String VARIABLES_PARAMETER = "vars";
    private static final String PLATFORM_PARAMTER = "platform";
    private Collection<String> inAppFrames = Collections.emptyList();
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
        boolean inApp = !(removeCommonFramesWithEnclosing && commonWithEnclosing) && isFrameInApp(stackTraceElement);
        generator.writeBooleanField(IN_APP_PARAMETER, inApp);
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        generator.writeEndObject();
    }

    private void writeFrame(JsonGenerator generator, SentryStackTraceElement stackTraceElement)
            throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, stackTraceElement.getModule());
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getFunction());
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineno());
        generator.writeNumberField(COL_NO_PARAMETER, stackTraceElement.getColno());
        generator.writeStringField(PLATFORM_PARAMTER, stackTraceElement.getPlatform());
        generator.writeStringField(ABSOLUTE_PATH_PARAMETER, stackTraceElement.getAbsPath());
        generator.writeEndObject();
    }

    private boolean isFrameInApp(StackTraceElement stackTraceElement) {
        // TODO: A linear search is not efficient here, a Trie could be a better solution.
        for (String inAppFrame : inAppFrames) {
            String className = stackTraceElement.getClassName();
            if (className.startsWith(inAppFrame)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeInterface(JsonGenerator generator, StackTraceInterface stackTraceInterface) throws IOException {
        generator.writeStartObject();
        generator.writeArrayFieldStart(FRAMES_PARAMETER);
        if (stackTraceInterface.getSentryStackTrace().length == 0) {
            StackTraceElement[] stackTrace = stackTraceInterface.getStackTrace();
            int commonWithEnclosing = stackTraceInterface.getFramesCommonWithEnclosing();
            for (int i = stackTrace.length - 1; i >= 0; i--) {
                writeFrame(generator, stackTrace[i], commonWithEnclosing-- > 0);
            }
        } else {
            SentryStackTraceElement[] stackTrace = stackTraceInterface.getSentryStackTrace();
            for (int i = stackTrace.length - 1; i >= 0; i--) {
                writeFrame(generator, stackTrace[i]);
            }
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    public void setRemoveCommonFramesWithEnclosing(boolean removeCommonFramesWithEnclosing) {
        this.removeCommonFramesWithEnclosing = removeCommonFramesWithEnclosing;
    }

    public void setInAppFrames(Collection<String> inAppFrames) {
        this.inAppFrames = inAppFrames;
    }
}
