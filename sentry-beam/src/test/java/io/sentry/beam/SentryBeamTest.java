package io.sentry.beam;

import io.sentry.beam.SentryBeam.Builder;
import io.sentry.beam.SentryBeam;

import mockit.*;
import org.testng.annotations.Test;

import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.GlobalWindow;
import org.apache.beam.sdk.transforms.windowing.PaneInfo;
import org.joda.time.Instant;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class SentryBeamTest {
    public interface TestOptions extends PipelineOptions {
        String getInputFile();
        void setInputFile(String value);

        String getOutput();
        void setOutput(String value);

        String getSentryDsn();
        void setSentryDsn(String value);
    }

    private class TestBuilder extends Builder {
        public HashMap<String, String> tags = new HashMap<>();

        @Override
        public void addTag(String name, String value) {
            tags.put(name, value);
        }
    }

    @Test
    public void sentryBeamUsingBuilderWithTimeStamp() {
        Instant timestamp = new Instant(1558328400000L);
        TestBuilder builder = (TestBuilder)(new TestBuilder().withTimestamp(timestamp));
        builder.attachTags();

        assertThat(builder.tags.get("timestamp"), equalTo("2019-05-20T05:00:00.000Z"));
    }

    @Test
    public void sentryBeamUsingBuilderWithBoundedWindow() {
        BoundedWindow boundedWindow = GlobalWindow.INSTANCE;
        TestBuilder builder = (TestBuilder)(new TestBuilder().withBoundedWindow(boundedWindow));
        builder.attachTags();

        assertThat(builder.tags.get("max_timestamp"), equalTo("294247-01-09T04:00:54.775Z (end of global window)"));
    }

    @Test
    public void sentryBeamUsingBuilderWithPaneInfo() {
        PaneInfo paneInfo = PaneInfo.createPane(true, false, PaneInfo.Timing.ON_TIME);
        TestBuilder builder = (TestBuilder)(new TestBuilder().withPaneInfo(paneInfo));
        builder.attachTags();

        assertThat(builder.tags.get("first_pane"), equalTo("true"));
        assertThat(builder.tags.get("last_pane"), equalTo("false"));
        assertThat(builder.tags.get("unknown_pane"), equalTo("false"));
    }

    @Test
    public void sentryBeamUsingPipelineOptions() {
        String[] args = {
            "--inputFile=input_file.txt",
            "--output=output.txt",
            "--sentryDsn=noop://localhost/1",
             "--runner=DirectRunner",
        };
        PipelineOptions pipelineOptions =
            PipelineOptionsFactory.fromArgs(args).as(TestOptions.class);
        TestBuilder builder = (TestBuilder)(new TestBuilder().withPipelineOptions(pipelineOptions));
        builder.attachTags();

        assertThat(builder.tags.get("job_name"), equalTo(pipelineOptions.getJobName()));
        assertThat(builder.tags.get("options_id"), equalTo(Long.toString(pipelineOptions.getOptionsId())));
        assertThat(builder.tags.get("user_agent"), equalTo(pipelineOptions.getUserAgent()));
        assertThat(builder.tags.get("pipeline_runner_class"), equalTo("org.apache.beam.runners.direct.DirectRunner"));
    }
}
