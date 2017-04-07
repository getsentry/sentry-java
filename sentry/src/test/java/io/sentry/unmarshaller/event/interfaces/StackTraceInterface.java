package io.sentry.unmarshaller.event.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class StackTraceInterface {
    @JsonProperty(value = "frames")
    private List<StackFrame> stackFrames;

    public static class StackFrame {
        @JsonProperty(value = "filename")
        private String fileName;
        @JsonProperty(value = "function")
        private String function;
        @JsonProperty(value = "module")
        private String module;
        @JsonProperty(value = "lineno")
        private int lineno;
        @JsonProperty(value = "colno")
        private int colno;
        @JsonProperty(value = "abs_path")
        private String absPath;
        @JsonProperty(value = "context_line")
        private String contextLine;
        @JsonProperty(value = "pre_context")
        private List<String> preContext;
        @JsonProperty(value = "post_context")
        private List<String> postContext;
        @JsonProperty(value = "in_app")
        private boolean inApp;
        @JsonProperty(value = "vars")
        private Object vars;
        @JsonProperty(value = "platform")
        private Object platform;
    }
}
