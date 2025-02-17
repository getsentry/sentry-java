package io.sentry.test

import io.sentry.Sentry
import io.sentry.Sentry.OptionsConfiguration
import io.sentry.SentryOptions

fun initForTest(optionsConfiguration: OptionsConfiguration<SentryOptions>) {
    Sentry.init {
        applyTestOptions(it)
        optionsConfiguration.configure(it)
    }
}

fun initForTest(optionsConfiguration: OptionsConfiguration<SentryOptions>, globalHubMode: Boolean) {
    Sentry.init({
        applyTestOptions(it)
        optionsConfiguration.configure(it)
    }, globalHubMode)
}

fun initForTest(dsn: String) {
    Sentry.init {
        applyTestOptions(it)
        it.dsn = dsn
    }
}

fun initForTest(options: SentryOptions) {
    applyTestOptions(options)
    Sentry.init(options)
}

fun initForTest() {
    Sentry.init()
}

fun applyTestOptions(options: SentryOptions) {
    options.shutdownTimeoutMillis = 0
}
