package io.sentry.beam;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.beam.event.helper.BeamEventBuilderHelper;
import io.sentry.connection.Connection;
import io.sentry.dsn.Dsn;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.PaneInfo;
import org.joda.time.Instant;

public class BeamSentryClientFactory extends DefaultSentryClientFactory {
    private Instant timestamp = null;
    private BoundedWindow boundedWindow = null;
    private PaneInfo paneInfo = null;
    private PipelineOptions pipelineOptions = null;

    private BeamSentryClientFactory() {
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        SentryClient sentryClientInstance = super.createSentryClient(dsn);

        BeamEventBuilderHelper eventBuilderHelper = new BeamEventBuilderHelper();
        eventBuilderHelper.timestamp = timestamp;
        eventBuilderHelper.boundedWindow = boundedWindow;
        eventBuilderHelper.paneInfo = paneInfo;
        eventBuilderHelper.pipelineOptions = pipelineOptions;
        sentryClientInstance.addBuilderHelper(eventBuilderHelper);

        return sentryClientInstance;
    }

    public static class Builder {
        private Instant timestamp = null;
        private BoundedWindow boundedWindow = null;
        private PaneInfo paneInfo = null;
        private PipelineOptions pipelineOptions = null;

        public BeamSentryClientFactory build() {
            BeamSentryClientFactory factory = new BeamSentryClientFactory();
            factory.timestamp = timestamp;
            factory.boundedWindow = boundedWindow;
            factory.paneInfo = paneInfo;
            factory.pipelineOptions = pipelineOptions;
            return factory;
        }

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
    }

    public static void main(String[] args) {
        System.out.println("hello world");
    }
}
