# `io.sentry.compose.navigation3.internal.backstack`

This package contains logic for observing a host app's back stack so that it can be converted into Sentry navigation data, including screen
names, navigation breadcrumbs, and transactions.

It also contains implementations of our built-in `BackStackEntryMapper`s that host apps can use to generate names and optional arguments
from their back stack.
