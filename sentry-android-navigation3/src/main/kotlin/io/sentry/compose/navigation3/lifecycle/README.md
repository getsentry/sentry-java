# `io.sentry.compose.navigation3.lifecycle`

Package contains code for coordinating multiple `SentryNavEffect`s and their access to a shared `IScope`, so that overlapping effects (e.g.,
during Activity or Fragment transitions) don't clobber one another's screen names, nav context, or nav transactions.

See the `NavLeaseCoordinator` KDoc for the lease policy that decides which effect may write to the scope.

## Encapsulation policy

This package is meant to encapsulate knowledge about the possibility of multiple `SentryNavEffect`s and the shared mutable state issues it
raises. Addressing those concerns is complicated. Confining them here makes them easier to reason about and easier to swap for alternative
logic should we someday need to.

## Limitations

Support for multiple `SentryNavEffect`s is restricted to the scenarios documented in the
[Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/#supported-setups).

`NavLeaseCoordinator` implements the policy that underlies those scenarios. It assumes that, at any moment, the user is interacting with at
most one registered `LifecycleOwner` in the host app, and that the `LifecycleOwner` being interacted with is the one that was most recently
resumed.

Concretely, a host app can safely use more than one `SentryNavEffect` only if:

1. each `SentryNavEffect` is called in the same composable as the `NavDisplay` it observes;
2. no `SentryNavEffect` is called from inside a `NavDisplay`, e.g., from a nav entry, scene, or entry decorator†;
3. each Activity or Fragment calls at most one `SentryNavEffect` from its own content (sibling Fragments may each have their own effect, but
   not alongside one in the Activity hosting them); and
4. the user can interact with only one of those Activities or Fragments at a time.

† Meant to ensure each effect's `LocalLifecycleOwner` is an Activity or a Fragment and not a `NavDisplay`-provided owner. `NavDisplay`
provides its own `LocalLifecycleOwner` to the entries and scenes it renders. That owner drops to STARTED state during transitions and while
overlays are shown. An effect called inside a `NavDisplay` would therefore (wrongly) gain and lose the nav lease `NavLeaseCoordinator`
manages as navigation animates, rather than as its Activity or Fragment comes and goes.
