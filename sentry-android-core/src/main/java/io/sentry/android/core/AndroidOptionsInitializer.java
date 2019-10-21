package io.sentry.android.core;

import android.content.Context;
import io.sentry.core.SentryOptions;

class AndroidOptionsInitializer {
  static void init(SentryOptions options, Context context) {
    // Firstly set the logger, if `debug=true` configured, logging can start asap.
    options.setLogger(new AndroidLogger());

    // TODO this needs to fetch the data from somewhere - defined at build time?
    options.setSentryClientName("sentry-android/0.0.1");

    ManifestMetadataReader.applyMetadata(context, options);
    options.addEventProcessor(new DefaultAndroidEventProcessor(context, options));
    options.setSerializer(new AndroidSerializer(options.getLogger()));
  }
}
