package io.sentry.test

import io.sentry.Sentry
import io.sentry.Sentry.OptionsConfiguration
import io.sentry.SentryOptions

/**
 * Sentry.init seals the options, but fixtures commonly keep configuring them afterwards. Undo the
 * seal so those fixtures keep working; SentryOptionsSealTest covers the production behaviour.
 */
private fun initAndUnseal(globalHubMode: Boolean? = null, configure: (SentryOptions) -> Unit) {
  var configured: SentryOptions? = null
  val configuration =
    OptionsConfiguration<SentryOptions> {
      applyTestOptions(it)
      configure(it)
      configured = it
    }
  if (globalHubMode == null) {
    Sentry.init(configuration)
  } else {
    Sentry.init(configuration, globalHubMode)
  }
  configured?.unseal()
}

fun initForTest(optionsConfiguration: OptionsConfiguration<SentryOptions>) {
  initAndUnseal { optionsConfiguration.configure(it) }
}

fun initForTest(optionsConfiguration: OptionsConfiguration<SentryOptions>, globalHubMode: Boolean) {
  initAndUnseal(globalHubMode) { optionsConfiguration.configure(it) }
}

fun initForTest(dsn: String) {
  initAndUnseal { it.dsn = dsn }
}

fun initForTest(options: SentryOptions) {
  applyTestOptions(options)
  Sentry.init(options)
  options.unseal()
}

fun initForTest() {
  // Mirrors the no-arg Sentry.init(), which enables external configuration.
  initAndUnseal { it.isEnableExternalConfiguration = true }
}

fun applyTestOptions(options: SentryOptions) {
  options.shutdownTimeoutMillis = 0
  options.sessionFlushTimeoutMillis = 0
}
