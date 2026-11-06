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
root's nav data is reinstated, and so is its nav transaction if unfinished. Otherwise, the nav root gets a new nav transaction (see
[below](#returning-to-a-nav-root)).

> † The exception is an incoming nav root that isn't resumed while the leaseholder or the incoming
nav root's host Activity is, e.g., a preloaded ViewPager2 Fragment page next to the current one,
whether or not the current page has a `SentryNavEffect`. The user isn't interacting with such a
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
                             Restore txA if unfinished, else start a  │
                               new txA; bind it if the scope is free  │
                             Finish txB if unfinished                 │
```

### Non-candidate roots and lease withdrawal

An Activity or Fragment without a `SentryNavEffect` (aka, a "non-candidate root") can't acquire the lease. When one replaces the
leaseholder, the coordinator "withdraws" the lease instead, i.e., it unpublishes the leaseholder's nav state without handing off the lease,
leaving the transaction unbound but running until the leaseholder resumes.

Non-candidate roots never register, so the coordinator infers their presence from either of two signals:

- another Activity is created or resumed while the leaseholder isn't resumed, unless the resumed
  Activity is itself a nav root, which takes the lease when it resumes; or

- the leaseholder pauses while its host Activity stays resumed (e.g., a ViewPager2 swipe). (Note
  that an Activity pauses before its Fragments do when the Activity is backgrounded or covered.)

**Limitations**: Leaseholders whose host Activity is unknown are never withdrawn. Fragments that cover the leaseholder without pausing it
(e.g., dialogs) go undetected. These two signals are deliberately the only ones: other surfaces that cover a leaseholder without pausing it
(e.g., bottom sheets or Compose dialogs) aren't detected, and shouldn't be by adding signals here.

**Limitation (below API 29)**: An Activity created over the leaseholder doesn't get its `ui.load` transaction bound to the scope. There's no
`onActivityPreCreated` below API 29, so the lease is withdrawn only after `ActivityLifecycleIntegration` has tried to bind the Activity's
`ui.load` transaction and found the leaseholder's nav transaction in the scope's transaction slot. The integration doesn't try again, so its
`ui.load` transaction stays unbound, as it did before withdrawal existed. Withdrawal still keeps the Activity's errors and spans from being
attributed to the leaseholder: spans the Activity produces attach to no transaction until one is bound (e.g., the nav transaction for the
first destination of the Activity's own `SentryNavEffect`, if it has one). Closing this gap would take a change to
`ActivityLifecycleIntegration` that affects every app, so it's deliberately left open.

As a result, an Activity with its own `SentryNavEffect` gets a different transaction bound for its spans depending on the API level. On
API 29+, its `ui.load` transaction is bound, and its first destination's nav transaction isn't started because the slot is taken. Below
API 29, when the leaseholder's nav transaction held the slot as the Activity was created, its first destination's nav transaction is bound
instead. Either way, `ActivityLifecycleIntegration` still sends the `ui.load` transaction, including its TTID and TTFD spans. Only the
parent of auto-instrumented spans (e.g., HTTP or database spans) differs.

### Returning to a nav root

A nav root is "returned to" when its candidate acquires the lease at any time other than on its first back stack update, e.g., when the
Activity covering it closes, or when the user swipes back to its ViewPager2 page. Spans produced on return (e.g., by work the nav root
resumes) need a nav transaction to attach to:

- if the candidate's nav transaction is still active, that transaction is bound again, which
  keeps the spans with the transaction that already holds the destination's earlier spans; and

- if the candidate's nav transaction is finished (e.g., the transaction timed out while another
  nav root held the lease, or it never started because another transaction held the scope's
  transaction slot), a new nav transaction is started for the current destination.

Either way, the transaction is bound only if the scope's transaction slot is free (see `StagedNavTransaction`). Rebinding and starting both
happen when the candidate publishes (`INavStatePublisher.publishTo`) its nav state, so the coordinator needn't tell a return from a first
acquisition: on its first back stack update, a candidate has no destination to start a transaction for yet.

When the user returns by closing an Activity, that Activity's `ui.load` transaction may still be running and hold the slot. It normally
leaves the slot when the closed Activity is destroyed, which happens after the nav root has resumed. If the Activity is destroyed within
`UI_LOAD_RETRY_WINDOW_NANOS` of the blocked attempt, binding the nav root's transaction is retried once (see `onAnyActivityDestroyed`).
Otherwise, the nav root goes without a bound nav transaction until its next navigation or return.

Only the current leaseholder is asked to retry when an Activity is destroyed. Its publisher checks whether the blocked `ui.load` has left
the slot and whether the retry window is still open. A leaseholder that loses or withdraws its lease forgets the blocked publication.

The retry has three gaps:

- A `ui.action` transaction started by the gesture that caused the return (e.g., a ViewPager2 swipe or an in-app back button) can block the
  nav transaction. Only Activity `ui.load` transactions trigger this retry.
- Spans produced between the nav root's resume and the closed Activity's destruction may attach to the closed Activity's `ui.load`.
- An Activity left without being destroyed (e.g., when another Activity is reordered to the front) triggers no retry.

Unbinding a finishing Activity's `ui.load` when it pauses would close the last two gaps, but would change `ActivityLifecycleIntegration`
for every app. Giving navigation priority over `ui.action` is an SDK-wide scope-slot policy decision shared with other integrations.

A nav root that's destroyed and recreated after losing the lease (e.g., on a configuration change) isn't returned to: it registers anew and
gets a transaction on its first back stack update. See [#6454](https://github.com/getsentry/sentry-java/issues/6454).

Bringing the app back to the foreground isn't a return either: the leaseholder keeps the lease while the app is in the background (see
`stopCandidate`), so it starts no new nav transaction when it resumes. This matches the rest of the SDK, which starts no transaction when an
existing Activity comes back to the foreground.

### Android ordering assumptions

Besides the callback ordering `NavActivityWatcher` relies on, `NavLeaseCoordinator` relies on the following sequences, which Android doesn't
document but AOSP and androidx have historically upheld:

- An Activity's `onActivityResumed` callbacks run before its `Lifecycle` dispatches
  `ON_RESUME` (see `isActivityNavRoot`).

- An Activity's `Lifecycle` dispatches `ON_PAUSE` before its Fragments'
  lifecycles do. androidx's `ReportFragment` and `FragmentActivity` ensure this, not the
  platform.

- A visible Activity that finishes is destroyed once the Activity that replaces it reports idle,
  or when the system's idle timeout fires (see `UI_LOAD_RETRY_WINDOW_NANOS`).

Going forward, if an order changes, nav data may be attributed to the wrong nav root, or a nav transaction may go unbound, but nothing would
throw.
