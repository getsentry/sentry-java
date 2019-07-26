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

/**
 * SentryClientFactory that provides Beam-specific construction, like extracting useful tags.
 */
public class BeamSentryClientFactory extends DefaultSentryClientFactory {
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

    /**
     * Make the constructor private, force the use of the builder.
     */
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

    /**
     * The builder class to create a new BeamSentryClientFactory.
     */
    public static class Builder {
        private Instant timestamp = null;
        private BoundedWindow boundedWindow = null;
        private PaneInfo paneInfo = null;
        private PipelineOptions pipelineOptions = null;

        /**
         * Build an instance of BeamSentryClientFactory with the configured options.
         *
         * @return BeamSentryClientFactory
         */
        public BeamSentryClientFactory build() {
            BeamSentryClientFactory factory = new BeamSentryClientFactory();
            factory.timestamp = timestamp;
            factory.boundedWindow = boundedWindow;
            factory.paneInfo = paneInfo;
            factory.pipelineOptions = pipelineOptions;
            return factory;
        }

        /**
         * Configure the BeamSentryClientFactory with the timestamp.
         *
         * @param timestamp Element timestamp.
         * @return Builder
         */
        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Configure the BeamSentryClientFactory with the bounded window.
         *
         * @param boundedWindow The window the element belongs to.
         * @return Builder
         */
        public Builder withBoundedWindow(BoundedWindow boundedWindow) {
            this.boundedWindow = boundedWindow;
            return this;
        }

        /**
         * Configure the BeamSentryClientFactory with the panel info.
         *
         * @param paneInfo The info of the pane the element belongs to.
         * @return Builder
         */
        public Builder withPaneInfo(PaneInfo paneInfo) {
            this.paneInfo = paneInfo;
            return this;
        }

        /**
         * Configure the BeamSentryClientFactory with the pipeline options.
         *
         * @param PipelineOptions The options of the current pipeline.
         * @return Builder
         */
        public Builder withPipelineOptions(PipelineOptions pipelineOptions) {
            this.pipelineOptions = pipelineOptions;
            return this;
        }
    }
}
