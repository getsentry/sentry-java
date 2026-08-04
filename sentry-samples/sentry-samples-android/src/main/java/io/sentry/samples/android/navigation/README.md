# Navigation Samples

This package contains the Android navigation sample surfaces used to validate Sentry's Nav2 and
Nav3 integrations in realistic flows.

## Package Layout

- `common/`: shared UI, transaction history, route-work helpers, and tracing utilities
- `nav2/`: the `Nav2Activity` sample and its fragment/Compose scenarios
- `nav3/`: the `Nav3Activity` sample and its Navigation 3 scenarios
- `NavSampleConfig.kt`: launch-time config plumbing shared by the Nav2 and Nav3 samples

## What These Samples Exercise

- navigation breadcrumbs
- route-level navigation transactions
- screen tracking
- route argument capture
- overlays vs real destinations
- interaction with `ui.load` transactions
- Nav2/Nav3 ownership and handoff behavior in the `Interop` tab

## Running The Samples

Install the sample app:

```bash
./gradlew :sentry-samples:sentry-samples-android:installDebug
```

Then open either:

- `Nav2Activity` for `NavController`-based scenarios
- `Nav3Activity` for Navigation 3 scenarios

Debug builds emit SDK logs to logcat under the `Sentry` tag:

```bash
adb logcat -s Sentry
```

## Configuration Notes

- The navigation samples expose toggles for navigation transactions, breadcrumbs, screen tracking,
  Activity `ui.load` transactions, and user interaction instrumentation.
- Automatic Activity tracing can overlap with route transactions. When testing route ownership,
  setting `Activity ui.load transaction` off usually makes the results easier to reason about.
- The Nav3 sample also lets you toggle captured back stack state and the maximum number of retained
  back stack entries.

## Nav3 Interop Lab

The `Nav3 Interop` tab in `Nav2Activity` exists to test mixed Nav2/Nav3 ownership inside one
Activity while still feeling like a normal sample flow.

It uses the real integrations:

- Nav2: `withSentryObservableEffect(...)`
- Nav3: `SentryNavEffect(...)`

Instead of a synthetic button matrix, the tab uses familiar product-flow screens and keeps topology
selection as a secondary control.

### Interop Modes

1. `Nav2 parent -> Nav3 child`
2. `Nav3 parent -> Nav2 child`
3. `Sibling roots`

### What To Watch

During each scenario, watch these surfaces together:

- the Ownership HUD in the tab
- the Nav2 top bar route and back stack text
- the Recent Transactions bottom sheet
- the scope `screen` value shown in the HUD
- the scope transaction name and operation shown in the HUD

### Core Expectations

- Both integrations may emit `navigation` breadcrumbs for their own route changes.
- Only one route transaction should effectively own the ambient scope at a time.
- The active scope transaction should usually have `op = navigation`.
- The scope `screen` should track the visible route owner.
- Hidden-host mutations are the most likely way to expose ownership or cleanup bugs.

## Manual Test Script

Use this script when validating the `Nav3 Interop` tab.

### General Failure Signs

Capture a bug if any of these happen:

1. visible route and scope `screen` disagree
2. visible route and active scope transaction disagree
3. `Emit a span` attaches to the wrong host's transaction
4. returning from a child host leaves no active route transaction on the parent
5. a hidden-host mutation steals ownership from the visible host
6. one visible navigation step creates duplicate Nav2 and Nav3 route transactions
7. the first child route never gets its own transaction
8. a back step restores the wrong owner transaction

### Mode 1: Nav2 Parent -> Nav3 Child

Start state:

- open `Nav2Activity`
- switch to `Nav3 Interop`
- select `Nav2 parent -> Nav3 child`

Expected initial state:

- visible host: `Nav2`
- current route: `/Nav2Home`
- Nav2 stack: `/Nav2Home`
- Nav3 stack: `/Nav3ProductDetail`
- scope `screen`: usually `/Nav2Home`
- scope transaction: usually `Nav2Home (navigation)` once the first render settles

#### Scenario A: Navigate in the parent, then hand off

1. Tap `Browse Products`.
2. Tap `Open Product 42`.
3. Tap `Continue in Nav3`.

Expected:

- Nav2 advances through `/Nav2ProductList` and `/Nav2ProductDetail`
- the nested Nav3 child becomes visible on `/Nav3ProductDetail`
- scope `screen` and transaction ownership move from Nav2 to Nav3 when the child becomes visible

Likely bug shapes:

- scope transaction stays on `/Nav2Nav3Host`
- scope `screen` remains on Nav2 while the child Nav3 screen is visible
- parent and child both create competing route transactions for one visible handoff

#### Scenario B: Continue in the child flow

1. Tap `Go to Checkout`.
2. Tap `Complete Order`.
3. Tap `Emit a span` on one of the child screens.

Expected:

- Nav3 advances through `/Nav3Checkout` and `/Nav3Confirmation`
- the child span attaches to the active Nav3 route transaction
- no extra route transaction should be created by `Emit a span` alone

Bug:

- the child span lands under a Nav2 transaction or under no route transaction

#### Scenario C: Return to the parent

1. Use system back until the parent flow is visible again.

Expected:

- the child flow resets to `/Nav3ProductDetail`
- the visible route returns to the Nav2 parent flow
- scope `screen` and scope transaction return to the active Nav2 parent route

High-value failure:

- Nav3 cleanup clears `screen` or the active transaction after Nav2 has already resumed ownership

### Mode 2: Nav3 Parent -> Nav2 Child

Select `Nav3 parent -> Nav2 child`.

Expected initial state:

- visible host: `Nav3`
- current route: `/Nav3Home`
- Nav3 stack: `/Nav3Home`
- Nav2 stack: `/Nav2ProductDetail`
- scope `screen`: usually `/Nav3Home`
- scope transaction: usually `Nav3Home (navigation)`

#### Scenario A: Navigate in the parent, then hand off

1. Tap `Browse Products`.
2. Tap `Open Product 42`.
3. Tap `Continue in Nav2`.

Expected:

- Nav3 advances through `/Nav3ProductList` and `/Nav3ProductDetail`
- the nested Nav2 child becomes visible on `/Nav2ProductDetail`
- scope `screen` and transaction ownership move from Nav3 to Nav2 when the child becomes visible

#### Scenario B: Continue in the child flow

1. Tap `Go to Checkout`.
2. Tap `Complete Order`.
3. Tap `Emit a span` on one of the child screens.

Expected:

- Nav2 advances through `/Nav2Checkout` and `/Nav2Confirmation`
- the child span attaches to the active Nav2 route transaction

Bug:

- the child span lands under the Nav3 parent transaction instead

#### Scenario C: Return to the parent

1. Use system back until the parent flow is visible again.

Expected:

- the child flow resets to `/Nav2ProductDetail`
- the visible route returns to the Nav3 parent flow
- scope `screen` and transaction return to the active Nav3 parent route

High-value failure:

- a stale Nav2 transaction remains bound and prevents Nav3 from cleanly resuming ownership

### Mode 3: Sibling Roots

Select `Sibling roots`.

Expected initial state:

- visible host: `Nav2`
- current route: `/Nav2Home`
- Nav2 stack: `/Nav2Home`
- Nav3 stack: `/Nav3Home`

This mode is the best way to catch stale ownership and hidden-host mutation bugs.

#### Scenario A: Build state in Nav2, then hand off visibility to Nav3

1. In Nav2, tap `Browse Products`.
2. Tap `Open Product 42`.
3. Tap `Go to Checkout`.
4. Tap `Show Nav3`.
5. In Nav3, tap `Browse Products`.
6. Tap `Open Product 42`.

Expected:

- Nav2 reaches `/Nav2Checkout`
- switching visibility moves the visible owner to `/Nav3Home`
- Nav3 then advances to `/Nav3ProductDetail`
- scope `screen` and transaction follow whichever host is currently visible

Bug:

- switching visibility does not switch ownership cleanly

#### Scenario B: Mutate the hidden Nav2 host

1. Keep Nav3 visible.
2. Tap `Mutate hidden Nav2`.

Expected:

- hidden Nav2 may change its own stack
- visible Nav3 should remain the active `screen` and transaction owner

Bug:

- scope `screen` flips back to a Nav2 route while Nav3 is still visible
- active transaction changes to a hidden Nav2 route
- hidden mutation emits transactions or breadcrumbs that look like visible ownership changes

#### Scenario C: Switch back to Nav2

1. Tap `Show Nav2`.

Expected:

- current route reflects the mutated Nav2 stack
- scope `screen` and transaction intentionally move to Nav2 when it becomes visible again

#### Scenario D: Mutate the hidden Nav3 host

1. Keep Nav2 visible.
2. Tap `Mutate hidden Nav3`.

Expected:

- hidden Nav3 changes should not steal visible ownership from Nav2

#### Scenario E: Back within each visible host

1. Use `Back to ...` buttons inside the visible Nav2 flow.
2. Tap `Show Nav3`.
3. Use `Back to ...` buttons inside the visible Nav3 flow.

Expected:

- each host only mutates its own stack when visible
- the hidden host keeps its own state until explicitly shown or mutated

## Recent Transactions Sheet Expectations

Check the drawer after each scenario cluster, not after every button press.

Healthy patterns:

- one finished transaction per visible route transition
- transaction names match the HUD route
- emitted child spans attach to the currently visible route transaction
- child-host entry or exit finishes the previous owner transaction and starts a new one cleanly

Bad patterns:

- two route transactions for one visible step
- missing transaction for a visible route change
- child span attached to the previous host's route
- transaction names lagging behind the visible route
- no route transaction resumes after returning from a child host

## Breadcrumb Expectations

When you inspect breadcrumbs in Sentry:

- `from` and `to` should line up with the stack changes you just drove
- one user-visible step should not usually produce two competing Nav2 and Nav3 breadcrumbs
- hidden-host mutations are useful because they reveal whether offscreen navigation leaks into the
  visible breadcrumb story

## Suggested Logging Template

Use this format when capturing findings:

```text
Mode: Nav2 parent -> Nav3 child
Step: System back to parent
Visible route: /Nav2ProductDetail
Screen: /Nav2ProductDetail
Transaction: Nav2ProductDetail (navigation)
Result: pass | bug
Notes: Nav3 cleanup cleared screen after parent resumed
```

## Recommended First Pass

If you only have time for a short pass, run these first:

1. `Nav2 parent -> Nav3 child`: browse products, open product 42, continue in Nav3, go to checkout,
   return to parent
2. `Nav3 parent -> Nav2 child`: browse products, open product 42, continue in Nav2, go to checkout,
   return to parent
3. `Sibling roots`: advance in Nav2, show Nav3, advance in Nav3, mutate hidden Nav2

Those three paths reveal the most important interop bugs fastest.
