package io.sentry

// Legacy DSN includes a secret. Sentry 8 and older will require it.
const val dsnStringLegacy: String =
  "https://d4d82fc1c2c4032a83f3a29aa3a3aff:ed0a8589a0bb4d4793ac4c70375f3d65@fake-sentry.io:65535/2147483647"
const val dsnString: String =
  "https://d4d82fc1c2c4032a83f3a29aa3a3aff@fake-sentry.io:65535/2147483647"
