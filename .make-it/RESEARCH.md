> Valid for this sprint only. Delete when this feature ships.

# Research: Navigation 3 Multiple Backstacks

## Sources Checked

- nav3-recipes README at `https://github.com/android/nav3-recipes`
- nav3-recipes multiple stacks:
  - `app/src/main/java/com/example/nav3recipes/multiplestacks/MultipleStacksActivity.kt`
  - `app/src/main/java/com/example/nav3recipes/multiplestacks/NavigationState.kt`
  - `app/src/main/java/com/example/nav3recipes/multiplestacks/Navigator.kt`
- nav3-recipes responsive navigation scene decorator:
  - `app/src/main/java/com/example/nav3recipes/navscenedecorator/ResponsiveNavigationSceneDecoratorActivity.kt`
  - `app/src/main/java/com/example/nav3recipes/navscenedecorator/NavigationState.kt`
- nav3-recipes custom scenes:
  - `app/src/main/java/com/example/nav3recipes/scenes/listdetail/ListDetailActivity.kt`
  - `app/src/main/java/com/example/nav3recipes/scenes/listdetail/ListDetailScene.kt`
  - `app/src/main/java/com/example/nav3recipes/scenes/twopane/TwoPaneActivity.kt`
- nav3-recipes Material adaptive scenes:
  - `app/src/main/java/com/example/nav3recipes/material/listdetail/MaterialListDetailActivity.kt`
  - `app/src/main/java/com/example/nav3recipes/material/supportingpane/MaterialSupportingPaneActivity.kt`
- Current branch implementation in `sentry-android-navigation3/src/main/kotlin/io/sentry/compose/navigation3/SentryNavEntryDecorator.kt`

The Android developer site URLs requested by the user failed to fetch from this environment with transport errors. The nav3-recipes repository was accessible and links back to the same Navigation 3 guide.

## Findings

### Navigation 3 State Is App-Owned

The multiple-stacks recipe does not expose a framework controller equivalent to Nav2's `NavHostController`. Instead, app code owns:

- a selected top-level route (`topLevelRoute`)
- a map of top-level routes to `NavBackStack`s (`backStacks`)
- navigation operations that mutate either the selected route or the selected route's stack

This means Sentry should not try to discover multiple stacks from a single controller. The integration needs either explicit inputs or generic primitives users can wire to app-owned state. The Phase 1 decision to add an explicit overload is consistent with this recipe.

### Multiple Stacks Can Render More Than One Stack

The basic multiple-stacks recipe can return entries for the start route and the selected route. The responsive navigation scene decorator recipe makes this explicit via `stacksInUse`:

```kotlin
val stacksInUse: List<NavKey>
    get() = if (topLevelRoute == startRoute) {
        listOf(startRoute)
    } else {
        listOf(startRoute, topLevelRoute)
    }
```

Those stacks are then flattened into entries for one `NavDisplay`. Therefore a crash context needs to distinguish:

- `selected_stack`: where app navigation actions currently go
- `stacks_in_use`: retained stacks being fed into the current display
- `visible_entries`: routes actually composed on screen after scene strategies run

This supports the chosen model better than a single `active_stack` field.

### `visible_entries` Must Carry Stack Name When Known

When entries from multiple stacks are flattened into one `NavDisplay`, route names alone are insufficient. A visible `/Home` from the home stack and `/Inbox` from the mail stack should be represented as:

```json
"visible_entries": [
  { "stack": "home", "route": "/Home" },
  { "stack": "mail", "route": "/Inbox" }
]
```

To do this, the multi-stack holder must remember route-to-stack ownership while observing the stack snapshots. The existing decorator receives only `contentKey` plus metadata when an entry is visible; it cannot infer stack ownership by itself in all cases.

### Primary Route Selection Cannot Depend Only On Hardcoded Metadata

The current draft has heuristics around list/detail metadata. nav3-recipes shows this is incomplete:

- Custom list/detail scenes use typed metadata keys with boolean values (`ListDetailScene.ListKey`, `ListDetailScene.DetailKey`).
- Custom two-pane scenes define their own metadata (`TwoPaneScene.twoPane()`).
- Material adaptive scenes use Material-provided helpers (`ListDetailSceneStrategy.listPane`, `detailPane`, `extraPane`, `SupportingPaneSceneStrategy.mainPane`, `supportingPane`, `extraPane`).
- Apps can define their own scene strategies with arbitrary metadata.

The integration should provide conservative default heuristics, but also expose `primaryRouteSelector` so apps can decide which visible entry should drive `scope.screen`, breadcrumb `to`, and transaction name.

### The Unified Context Model Is Feasible Before Release

The branch has not released the Navigation 3 module yet. Migrating the existing Phase 1/2 draft from `navigation.backstack` to the plural model avoids a near-term payload shape change.

Recommended single-stack context:

```json
{
  "navigation": {
    "selected_stack": "default",
    "stacks_in_use": ["default"],
    "backstacks": [
      {
        "name": "default",
        "selected": true,
        "in_use": true,
        "backstack": [{ "route": "/Home" }]
      }
    ]
  }
}
```

Recommended multiple-stack context:

```json
{
  "navigation": {
    "selected_stack": "mail",
    "stacks_in_use": ["home", "mail"],
    "backstacks": [
      {
        "name": "home",
        "selected": false,
        "in_use": true,
        "backstack": [{ "route": "/Home" }]
      },
      {
        "name": "mail",
        "selected": true,
        "in_use": true,
        "backstack": [{ "route": "/Inbox" }, { "route": "/Message" }]
      }
    ],
    "visible_entries": [
      { "stack": "home", "route": "/Home" },
      { "stack": "mail", "route": "/Inbox" }
    ]
  }
}
```

### Existing Branch Constraints

The draft module currently depends on `androidx.navigation3:navigation3-runtime` as `compileOnly` and test dependency. It exposes APIs based on `SnapshotStateList<T>`, `NavEntryDecorator<T>`, and `SentryNavStateHolder<T>`.

Adding public APIs will require `apiDump`. The `.api` file currently exposes `SentryNavStateHolder` and composable functions from `SentryNavEntryDecoratorKt`.

## Implementation Implications

- Add a public multiple-stack entry point that accepts selected stack, stack snapshots, stack name extraction, destination name extraction, argument extraction, and primary route selection.
- Consider a small public value type for primary selection input, because callbacks should not expose mutable holder internals.
- Migrate existing single-stack context writes to the same plural shape with `default` stack.
- Track route key ownership per stack so `visible_entries` can include `stack` when the decorator observes visibility.
- Keep `contexts.app.view_names` populated from visible entries where possible.
- Treat selected-stack changes as navigation events.
- Treat inactive retained stack changes as crash-context updates unless they become selected or visible.
- Keep guard behavior around all user-provided callbacks and host key methods.

## Risks

- Public callback types may be hard to evolve after release; keep them minimal and marked experimental.
- Stack snapshots may contain equal route keys across different stacks; ownership resolution must not assume route key uniqueness globally.
- Metadata heuristics can never fully cover custom scenes; docs should describe `primaryRouteSelector` clearly.
- If `stacks_in_use` is optional, defaulting it incorrectly could mark retained but invisible stacks as in use. Prefer accepting it explicitly or deriving it from selected stack plus visible stack ownership.
