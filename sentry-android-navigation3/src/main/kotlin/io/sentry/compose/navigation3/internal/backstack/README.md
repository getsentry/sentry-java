# `io.sentry.compose.navigation3.internal.backstack`

Package contains code for generating Sentry data from a host app's back stack.

## Conventions

### Properties vs. arguments

Code in this package uses the term "properties" for the fields and values it reads from a back stack entry and "arguments" for the values
a mapper supplies for Sentry to record, i.e., `SentryBackStackEntry.arguments`. Arguments include the mapper's output before sanitization,
which may modify or drop them before Sentry records them. Hence
`SerialNameAndProperties` and `KotlinxPropertiesEncoder`, but `SentryBackStackEntry(arguments = ...)`.

When a host app uses a built-in `BackStackEntryMapper`, we're responsible for mapping entry properties to corresponding arguments. When the
host app implements its own `BackStackEntryMapper`, it performs the mapping on its end and hands the resulting arguments to us.
