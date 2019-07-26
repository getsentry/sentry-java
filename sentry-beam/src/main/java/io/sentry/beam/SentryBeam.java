package io.sentry.beam;

import io.sentry.Sentry;
import io.sentry.event.Event;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.PipelineRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.PaneInfo;
import org.joda.time.Instant;

public final class SentryBeam  {
    /**
     * Hide constructor
     */
    private SentryBeam() {

    }

    public static void capture(Event event) {
        new Builder().capture(event);
    }

    public static void capture(Throwable throwable) {
        new Builder().capture(throwable);
    }

    public static void capture(String message) {
        new Builder().capture(message);
    }

    public static Builder withTimestamp(Instant timestamp) {
        return new Builder().withTimestamp(timestamp);
    }

    public static Builder withBoundedWindow(BoundedWindow boundedWindow) {
        return new Builder().withBoundedWindow(boundedWindow);
    }

    public static Builder withPaneInfo(PaneInfo paneInfo) {
        return new Builder().withPaneInfo(paneInfo);
    }

    public static Builder withPipelineOptions(PipelineOptions pipelineOptions) {
        return new Builder().withPipelineOptions(pipelineOptions);
    }

    public static class Builder {
        /**
         * The Instant to extract timestamp information from.
         */
        private Instant timestamp = null;

        /**
         * The bounded window to extract window information from.
         */
        private BoundedWindow boundedWindow = null;

        /**
         * The pane info to extract pane information from.
         */
        private PaneInfo paneInfo = null;

        /**
         * The pipeline options to extract pipeline information from.
         */
        private PipelineOptions pipelineOptions = null;

        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withBoundedWindow(BoundedWindow boundedWindow) {
            this.boundedWindow = boundedWindow;
            return this;
        }

        public Builder withPaneInfo(PaneInfo paneInfo) {
            this.paneInfo = paneInfo;
            return this;
        }

        public Builder withPipelineOptions(PipelineOptions pipelineOptions) {
            this.pipelineOptions = pipelineOptions;
            return this;
        }

        public void capture(Event event) {
            attachTags();
            Sentry.capture(event);
        }

        public void capture(Throwable throwable) {
            attachTags();
            Sentry.capture(throwable);
        }

        public void capture(String message) {
            attachTags();
            Sentry.capture(message);
        }

        private void attachTags() {
            attachTimestamp();
            attachBoundedWindow();
            attachPaneInfo();
            attachPipelineOptions();
        }

        private void addTag(String name, String value) {
            Sentry.getContext().addTag(name, value);
        }

        /**
         * Attach tags with information from the timestamp.
         */
        private void attachTimestamp() {
            if (timestamp == null) {
                return;
            }

            addTag("timestamp", BoundedWindow.formatTimestamp(timestamp));
        }

        /**
         * Attach tags with information from the bounded window.
         */
        private void attachBoundedWindow() {
            if (boundedWindow == null) {
                return;
            }

            Instant instant = boundedWindow.maxTimestamp();
            if (instant != null) {
                addTag("max_timestamp", BoundedWindow.formatTimestamp(instant));
            }
        }

        /**
         * Attach tags with information from the pane info.
         */
        private void attachPaneInfo() {
            if (paneInfo == null) {
                return;
            }

            PaneInfo.Timing timing = paneInfo.getTiming();
            if (timing != null) {
                addTag("timing", timing.name());
            }

            addTag("first_pane", Boolean.toString(paneInfo.isFirst()));
            addTag("last_pane", Boolean.toString(paneInfo.isLast()));
            addTag("unknown_pane", Boolean.toString(paneInfo.isUnknown()));
        }

        /**
         * Attach tags with information from the pipeline options.
         */
        private void attachPipelineOptions() {
            if (pipelineOptions == null) {
                return;
            }

            String jobName = pipelineOptions.getJobName();
            if (jobName != null && !jobName.isEmpty()) {
                addTag("job_name", jobName);
            }

            addTag("options_id", Long.toString(pipelineOptions.getOptionsId()));

            String tempLocation = pipelineOptions.getTempLocation();
            if (tempLocation != null && !tempLocation.isEmpty()) {
                addTag("temp_location", tempLocation);
            }

            String userAgent = pipelineOptions.getUserAgent();
            if (userAgent != null && !userAgent.isEmpty()) {
                addTag("user_agent", userAgent);
            }

            Class<? extends PipelineRunner<?>> runner = pipelineOptions.getRunner();
            if (runner != null) {
                String runnerName = runner.getCanonicalName();
                if (runnerName != null && !runnerName.isEmpty()) {
                    addTag("pipeline_runner_class", runnerName);
                }
            }
        }
    }
}
