package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
    private void writeFrame(JsonGenerator generator, SentryStackTraceElement stackTraceElement,
                            boolean commonWithEnclosing) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, stackTraceElement.getModule());
        boolean inApp = !(removeCommonFramesWithEnclosing && commonWithEnclosing) && isFrameInApp(stackTraceElement);
        generator.writeBooleanField(IN_APP_PARAMETER, inApp);
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getFunction());
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineno());

        // Non-standard fields.
        if (stackTraceElement.getColno() != null) {
            generator.writeNumberField(COL_NO_PARAMETER, stackTraceElement.getColno());
        }

        if (stackTraceElement.getPlatform() != null) {
            generator.writeStringField(PLATFORM_PARAMTER, stackTraceElement.getPlatform());
        }

        if (stackTraceElement.getAbsPath() != null) {
            generator.writeStringField(ABSOLUTE_PATH_PARAMETER, stackTraceElement.getAbsPath());
        }

        if (stackTraceElement.getVars() != null) {
            generator.writeObjectFieldStart(VARIABLES_PARAMETER);
            for (Map.Entry<String, Object> varEntry : stackTraceElement.getVars().entrySet()) {
                String name = varEntry.getKey();
                Object value = varEntry.getValue();
                if (value == null) {
                    generator.writeNullField(name);
                } else {
                    generator.writeObjectField(name, value);
                }
            }
            generator.writeEndObject();
        }

        generator.writeEndObject();
    }

    private boolean isFrameInApp(SentryStackTraceElement stackTraceElement) {
        // TODO: A linear search is not efficient here, a Trie could be a better solution.
        for (String inAppFrame : inAppFrames) {
            String className = stackTraceElement.getModule();
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
        SentryStackTraceElement[] sentryStackTrace = stackTraceInterface.getStackTrace();
        int commonWithEnclosing = stackTraceInterface.getFramesCommonWithEnclosing();
        for (int i = sentryStackTrace.length - 1; i >= 0; i--) {
            writeFrame(generator, sentryStackTrace[i], commonWithEnclosing-- > 0);
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
