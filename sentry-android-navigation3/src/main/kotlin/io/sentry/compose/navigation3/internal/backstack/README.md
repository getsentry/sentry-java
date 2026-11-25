# `io.sentry.compose.navigation3.internal.backstack`

This package converts a host app's back stack into Sentry navigation data: screen names, navigation
breadcrumbs, transactions, and the captured back stack.

## Mapping and sanitization

The host app's `BackStackEntryMapper` supplies each entry's name and optional arguments in a
`SentryBackStackEntry`. Properties are the fields and values on the host's back stack entry;
arguments are the values the mapper chooses to expose as `SentryBackStackEntry.arguments`.

Arguments are the mapper's output before sanitization. `BackStackConverter` normalizes entry names
and sanitizes arguments before they are recorded. Sanitization can stringify values or drop an
entry's arguments when they are invalid or exceed the limits in `ArgumentConstraints`.
The value and character budgets are shared across the entries captured in one back stack update.

## Observation and coordination

`BackStackObserver` detects changes and records navigation data for one back stack.
`CoordinatedBackStackObserver` connects that observer to `NavScopes`, which coordinates access to
shared scope state with other navigation roots. The lifecycle and ownership rules live in the
[sibling lifecycle package](../lifecycle/README.md).
