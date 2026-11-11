# `io.sentry.compose.navigation3.lifecycle`

Package contains code for coordinating multiple `SentryNavEffect`s and their access to a shared `IScope`, so that overlapping effects (e.g.,
during Activity or Fragment transitions) don't clobber one another's screen names, nav context, or nav transactions.

This package is meant to encapsulate knowledge about the possibility of multiple `SentryNavEffect`s and the shared mutable state issues it
raises. Addressing those concerns is complicated. Confining them here makes them easier to reason about and easier to swap for alternative
logic should we someday need to.

## Limitations

Support for multiple `SentryNavEffect`s is restricted to the scenarios documented in the
[Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/#supported-setups).

Concretely, a host app can safely use more than one `SentryNavEffect` only if:

1. each `SentryNavEffect` is called in the same composable as the `NavDisplay` it observes;
2. no `SentryNavEffect` is called from inside a `NavDisplay`, e.g., from a nav entry, scene, or entry decorator†;
3. each Activity or Fragment calls at most one `SentryNavEffect` from its own content (sibling Fragments may each have their own effect, but
   not alongside one in the Activity hosting them); and
4. the user can interact with only one of those Activities or Fragments at a time.

> † Meant to ensure each effect's `LocalLifecycleOwner` is an Activity or a Fragment and not a `NavDisplay`-provided owner. `NavDisplay`
provides its own `LocalLifecycleOwner` to the entries and scenes it renders. That owner drops to STARTED state during transitions and while
overlays are shown. An effect called inside a `NavDisplay` would therefore (wrongly) gain and lose the nav lease `NavLeaseCoordinator`
manages as navigation animates, rather than as its Activity or Fragment comes and goes.

## Lease policy

`NavLeaseCoordinator` implements the lease policy that supports the scenarios from the Navigation for Android docs. The policy assumes that,
at any moment, the user is interacting with at most one nav root in the host app, and that the nav root being interacted with is the one
that was most recently resumed. (See the `NavRootInfo` KDoc for a definition of "nav root".)

### A fundamental tradeoff

While nav roots overlap (e.g., during an Activity transition), the nav transaction and scoped nav data can reflect **_either_**:

1. the outgoing nav root that's still visible, or
2. the incoming nav root that may crash before it becomes visible,

but not both. `NavLeaseCoordinator` opts for (2) so that the incoming destination populates crash context and any work performed is
associated with it† – the idea being that start-up logic is more likely to cause a crash or produce interesting spans than an outgoing
destination's steady state.

> † Associating work with the incoming transaction means spans produced by the outgoing nav root
may appear as if they were produced by the incoming nav root (or vice versa, were our policy
reversed).

### Lease handoff policy

The lease is acquired preemptively by an incoming nav root's `NavScopes` instance when it applies its first back stack update
(`onBackStackUpdate`).†

A displaced nav root remains eligible for lease restoration until it stops (`ON_STOP`). It gets the lease back when it resumes
(`ON_RESUME`), or sooner if the new nav root leaves or pauses while the displaced nav root is still visible. If restored, the displaced nav
root's nav data and unfinished transaction are reinstated.

> † The exception is an incoming nav root that isn't resumed while the leaseholder is, e.g., a
preloaded ViewPager2 Fragment page next to the current one. The user isn't interacting with such a
nav root yet, so it acquires the lease when it resumes instead.

The following diagram illustrates how handoff works:

```
 Time
   │
   ▼

Nav root A                    Shared IScope state                    Nav root B
──────────                    ───────────────────                    ──────────

attach()                      Navigation lease: none
  │
Initial back stack update
  │
  ├── acquire lease ────────► Navigation lease: A
  ├── create & bind txA ────► Scope transaction: txA
  ├── publish nav data ─────► Published data: A
  │
ON_RESUME                    A is eligible for restoration
  │
ON_PAUSE
  │
  ├────────────────────────► Navigation lease: A (no other resumed nav root)
  │                          A remains eligible for restoration
  │
  │                                                                attach()
  │                                                                   │
  │                          Navigation lease: A ◄────────────────────┤
  │                          Scope transaction: txA                   │
  │                          Published data: A                        │
  │                          B registered, awaiting initial back      │
  │                            stack update                           │
  │                                                                   │
  │                                                   Initial back stack update
  │                                                                   │
  │                          Suspend & unbind txA ◄───────────────────┤
  │                          Navigation lease: B                      │
  │                          Published data: B                        │
  │                          Scope transaction: txB                   │
  │                          (rollback point)                         │
  │                                                                   │
  │                                                              ON_RESUME
  │                                                                   │
  │                          B eligible for restoration ◄─────────────┤
  │                                                                   │
ON_STOP                                                               │
  │                                                                   │
  ├── retain txA; timers continue                                     │
  ├────────────────────────► A no longer eligible for restoration     │
  │                                                                   │
ON_DESTROY                                                            │
  │                                                                   │
  ├── finish retained txA if unfinished                               │
  └────────────────────────► A no longer tracked                      │
                                                                  ON_PAUSE
                                                                      │
                             Navigation lease: B (no other resumed  ◄─┤
                               nav root)                              │
                                                                      │
                                                                  ON_STOP
                                                                      │
                             Navigation lease: B (no other resumed  ◄─┤
                               nav root); B keeps recording           │
                               navigation                             │
                             B no longer eligible for restoration     │
                                                                      │
                                                                ON_DESTROY
                                                                      │
                             Finish txB if unfinished; ◄──────────────┤
                                unbind if still bound                 │
                             Clear B's remaining screen/back stack    │
                             Retain recorded nav events               │
                             Navigation lease: none                   │
                                                                      ▼

Alternative at rollback point: B disposes before A stops
                             Unbind txB ◄─────────────── dispose B ───┤
                             Navigation lease: A                      │
                             Republish A's staged data                │
                             Restore txA if unfinished and scope free │
                             Finish txB if unfinished                 │
```

### Nav transactions follow navigation, not the lease

A nav root gets a nav transaction for a destination it navigates to, never merely for acquiring the lease. A destination reached without
the lease gets its transaction when the lease is next acquired, if it can start then; otherwise it gets none. On reacquiring the lease
without having navigated in the meantime, a nav root only gets its unfinished transaction back, if any.

Hence the asymmetry for, e.g., ViewPager2 pages: the first time the user swipes to a preloaded page, it gets the transaction for the
destination it reached while offscreen, but swiping back to a page whose transaction has finished starts none.
