# `io.sentry.compose.navigation3.lifecycle`

Package contains code for coordinating multiple `SentryNavEffect`s and their access to a shared `IScope`, so that overlapping effects (e.g.,
during Activity or Fragment transitions) don't clobber one another's screen names, nav context, or nav transactions.

See the `NavScopes` KDoc for the lease policy that decides which effect may write to the scope.

## Encapsulation policy

This package is meant to encapsulate knowledge about the possibility of multiple `SentryNavEffect`s and the shared mutable state issues it
raises. Addressing those concerns is complicated. Confining them here makes them easier to reason about and easier to swap for alternative
logic should we someday need to.
