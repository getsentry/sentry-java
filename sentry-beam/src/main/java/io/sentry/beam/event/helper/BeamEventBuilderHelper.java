package io.sentry.beam.event.helper;

import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.PipelineRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.PaneInfo;
import org.joda.time.Instant;

public class BeamEventBuilderHelper implements EventBuilderHelper {
    public Instant timestamp = null;
    public BoundedWindow boundedWindow = null;
    public PaneInfo paneInfo = null;
    public PipelineOptions pipelineOptions = null;

    private void attachTimestamp(EventBuilder eventBuilder) {
        if (timestamp == null) {
            return;
        }

        eventBuilder.withTag("Timestamp", BoundedWindow.formatTimestamp(timestamp));
    }

    private void attachBoundedWindow(EventBuilder eventBuilder) {
        if (boundedWindow == null) {
            return;
        }

        Instant instant = boundedWindow.maxTimestamp();
        if (instant != null) {
            eventBuilder.withTag("Max Timestamp", BoundedWindow.formatTimestamp(instant));
        }
    }

    private void attachPaneInfo(EventBuilder eventBuilder) {
      if (paneInfo == null) {
          return;
      }

      PaneInfo.Timing timing = paneInfo.getTiming();
      if (timing != null) {
          eventBuilder.withTag("Timing", timing.name());
      }

      boolean first = paneInfo.isFirst();
      if (first) {
          eventBuilder.withTag("First Pane", Boolean.toString(first));
      }

      boolean last = paneInfo.isLast();
      if (last) {
          eventBuilder.withTag("Last Pane", Boolean.toString(last));
      }

      boolean unknown = paneInfo.isUnknown();
      if (unknown) {
          eventBuilder.withTag("Unknown Pane", Boolean.toString(unknown));
      }
    }

    private void attachPipelineOptions(EventBuilder eventBuilder) {
        if (pipelineOptions == null) {
            return;
        }

        String jobName = pipelineOptions.getJobName();
        if (jobName != null && !jobName.isEmpty()) {
            eventBuilder.withTag("Job Name", jobName);
        }

        eventBuilder.withTag("Options Id", Long.toString(pipelineOptions.getOptionsId()));

        String tempLocation = pipelineOptions.getTempLocation();
        if (tempLocation != null && !tempLocation.isEmpty()) {
            eventBuilder.withTag("Temp Location", tempLocation);
        }

        String userAgent = pipelineOptions.getUserAgent();
        if (userAgent != null && !userAgent.isEmpty()) {
            eventBuilder.withTag("User Agent", userAgent);
        }

        Class<? extends PipelineRunner<?>> runner = pipelineOptions.getRunner();
        if (runner != null) {
            String runnerName = runner.getCanonicalName();
            if (runnerName != null && !runnerName.isEmpty()) {
                eventBuilder.withTag("Pipeline Runner Class", runnerName);
            }
        }
    }

    @Override
    public void helpBuildingEvent(EventBuilder eventBuilder) {
        attachTimestamp(eventBuilder);
        attachBoundedWindow(eventBuilder);
        attachPaneInfo(eventBuilder);
        attachPipelineOptions(eventBuilder);
    }
}
