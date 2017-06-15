package io.sentry.event.interfaces;

import io.sentry.jvmti.Frame;
import io.sentry.jvmti.LocalsCache;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Richer StackTraceElement class.
 */
public class SentryStackTraceElement {
    private final String module;
    private final String function;
    private final String fileName;
    private final int lineno;
    private final Integer colno;
    private final String absPath;
    private final String platform;
    private final Map<String, Object> locals;

    /**
     * Construct a SentryStackTraceElement.
     *
     * @param module Module (class) name.
     * @param function Function (method) name.
     * @param fileName Filename.
     * @param lineno Line number.
     * @param colno Column number.
     * @param absPath Absolute path.
     * @param platform Platform name.
     * @param locals Local variables.
     */
    // CHECKSTYLE.OFF: ParameterNumber
    public SentryStackTraceElement(String module, String function, String fileName, int lineno,
                                   Integer colno, String absPath, String platform, Map<String, Object> locals) {
        this.module = module;
        this.function = function;
        this.fileName = fileName;
        this.lineno = lineno;
        this.colno = colno;
        this.absPath = absPath;
        this.platform = platform;
        this.locals = locals;
    }
    // CHECKSTYLE.ON: ParameterNumber

    public String getModule() {
        return module;
    }

    public String getFunction() {
        return function;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineno() {
        return lineno;
    }

    public Integer getColno() {
        return colno;
    }

    public String getAbsPath() {
        return absPath;
    }

    public String getPlatform() {
        return platform;
    }

    public Map<String, Object> getLocals() {
        return locals;
    }

    /**
     * Convert an array of {@link StackTraceElement}s to {@link SentryStackTraceElement}s.
     *
     * @param stackTraceElements Array of {@link StackTraceElement}s to convert.
     * @return Array of {@link SentryStackTraceElement}s.
     */
    public static SentryStackTraceElement[] fromStackTraceElements(StackTraceElement[] stackTraceElements) {
        Frame[] localsCache = LocalsCache.getCache();

        /*
        Verify the localsCache stacktrace length and method classes/names match the stackTraceElements
        length and method classes/names. This needs to be done in its own loop because the entire stack
        must be match before we can assume the stored locals are most likely from the stacktrace that
        is being recorded.

        The reason they might *not* match is that a user can stash an exception in a variable, allow
        another exception to occur, and then attempt to send the first exception to Sentry. Since we
        can't cache every exception's locals for all time, we attempt a best-effort where we store the
        last invocation and do this matching to see if they're most likely from the same call.

        This code is only run if:
        1. the JVM is running our agent (and therefore LocalsCache is not null)
        2. the length of the cached locals matches the length of the stacktrace that is being sent
         */
        boolean hasLocals = false;
        if (localsCache != null && localsCache.length == stackTraceElements.length) {
            hasLocals = true;
            for (int i = 0; i < stackTraceElements.length; i++) {
                StackTraceElement stackTraceElement = stackTraceElements[i];
                Method method = localsCache[i].getMethod();

                if (!stackTraceElement.getClassName().equals(method.getDeclaringClass().getName())
                    || !stackTraceElement.getMethodName().equals(method.getName())) {
                    hasLocals = false;
                }
            }
        }

        SentryStackTraceElement[] sentryStackTraceElements = new SentryStackTraceElement[stackTraceElements.length];
        for (int i = 0; i < stackTraceElements.length; i++) {
            sentryStackTraceElements[i] = fromStackTraceElement(stackTraceElements[i],
                hasLocals ? localsCache[i].getLocals() : null);
        }

        return sentryStackTraceElements;
    }

    /**
     * Convert a single {@link StackTraceElement} to a {@link SentryStackTraceElement}.
     *
     * @param stackTraceElement {@link StackTraceElement} to convert.
     * @return {@link SentryStackTraceElement}
     */
    public static SentryStackTraceElement fromStackTraceElement(StackTraceElement stackTraceElement) {
        return fromStackTraceElement(stackTraceElement, null);
    }

    private static SentryStackTraceElement fromStackTraceElement(StackTraceElement stackTraceElement,
                                                                 Map<String, Object> locals) {
        return new SentryStackTraceElement(
            stackTraceElement.getClassName(),
            stackTraceElement.getMethodName(),
            stackTraceElement.getFileName(),
            stackTraceElement.getLineNumber(),
            null,
            null,
            null,
            locals
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SentryStackTraceElement that = (SentryStackTraceElement) o;
        return lineno == that.lineno
            && Objects.equals(module, that.module)
            && Objects.equals(function, that.function)
            && Objects.equals(fileName, that.fileName)
            && Objects.equals(colno, that.colno)
            && Objects.equals(absPath, that.absPath)
            && Objects.equals(platform, that.platform)
            && Objects.equals(locals, that.locals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, function, fileName, lineno, colno, absPath, platform, locals);
    }

    @Override
    public String toString() {
        return "SentryStackTraceElement{"
            + "module='" + module + '\''
            + ", function='" + function + '\''
            + ", fileName='" + fileName + '\''
            + ", lineno=" + lineno
            + ", colno=" + colno
            + ", absPath='" + absPath + '\''
            + ", platform='" + platform + '\''
            + ", locals='" + locals + '\''
            + '}';
    }
}
