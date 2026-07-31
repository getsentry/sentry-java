# Idea Spec

## Problem

Sentry's draft Navigation 3 integration currently covers single-stack navigation and multipane visibility, but it does not model apps that retain multiple independent Nav3 back stacks, such as bottom-navigation apps where each top-level destination owns its own history. In Navigation 3, this state is app-owned rather than managed by a shared controller API, so Sentry cannot infer multiple-stack behavior from a single `NavDisplay` back stack alone.

Without explicit multiple-backstack support, Sentry events from these apps either lose retained inactive stack context or force users to flatten app-specific state into a single stack shape that does not match Navigation 3 recipes.

## Target Users

Android developers using the new `sentry-android-navigation3` integration with Jetpack Navigation 3 apps that maintain multiple retained back stacks, especially apps following nav3-recipes patterns for top-level routes, bottom navigation, responsive navigation scene decorators, and multipane layouts.

## Solution Overview

Add explicit multiple-backstack support to the Navigation 3 integration. The public API should accept an app-selected stack key plus a map of stack keys to stack snapshots, mirroring the Navigation 3 recipe model where apps own `topLevelRoute` and `backStacks`.

Use one clean navigation context model from the first Navigation 3 release, including single-stack, multipane, and multi-stack modes:

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

For single-stack apps, use a default stack name so the same plural model applies:

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

Switching the selected top-level stack should be treated as a navigation event: emit a breadcrumb from the previous selected stack's top route to the new selected stack's top route, update screen tracking, and start a navigation transaction if tracing is enabled.

Expose a `stackNameExtractor` callback so apps can turn arbitrary stack keys, including `NavKey` top-level routes from nav3-recipes, into stable readable names. Default to a route-like name derived from the stack key.

Expose a `primaryRouteSelector` escape hatch for multipane and custom scene strategies. Defaults should handle simple cases, but custom and Material Nav3 scenes use arbitrary metadata, so users need a way to pick the primary visible route for `scope.screen`, breadcrumbs, and transaction names.

## Key Constraints

- Navigation 3 does not provide one canonical controller for all navigation state; apps own back stacks and top-level route selection.
- The integration must not crash host apps if key equality, hashing, string conversion, or user extractors throw.
- The module is unreleased, so the draft Phase 1/2 context shape can still change to the cleaner plural model before first release.
- Public API additions affect `.api` files and must be regenerated with `apiDump`.
- Argument extraction remains opt-in and must preserve the privacy warning: returned arguments are sent as-is and are not gated by `sendDefaultPii`.
- Multiple-stack support must compose with multipane support. Retained backstack state and currently visible entries are separate concepts.
- Keep the integration generic. It should support nav3-recipes patterns without depending on their concrete `NavigationState` classes.

## Out of Scope

- Deep-link parsing or synthetic backstack construction.
- Owning or mutating app navigation state.
- Full Nav3 scene identity reporting, unless Nav3 exposes a stable scene signal that the integration can observe reliably.
- Automatic PII scrubbing of route arguments.
- Support for remote issue creation, PR creation, or publishing artifacts as part of make-it.

## Open Questions

- Exact Kotlin API shape for the multiple-stack overload: whether it lives as a new `SentryNav3MultiStackNavigationEffect`, an overload of `SentryNav3NavigationEffect`, a holder method, or a combination.
- Exact type signature for `primaryRouteSelector`, including whether it receives public data classes for visible entries and stack entries.
- Whether `stacks_in_use` should be user-supplied, inferred from visible entries, or default to all non-empty stacks when not provided.
- How much default metadata heuristic support to include for Material adaptive scenes versus documenting `primaryRouteSelector`.

## Success Criteria

- Single-stack and multipane draft behavior is migrated to the unified plural context model before release.
- Multiple retained back stacks appear in `contexts.navigation.backstacks` with stable stack names, selected/in-use flags, and capped route entries.
- Visible entries can include stack names when stack ownership is known.
- Switching selected stacks emits a breadcrumb, updates screen tracking, and starts a navigation transaction.
- Active-stack pushes/pops continue to behave like existing single-stack navigation events.
- Inactive retained stack changes update crash context without incorrectly becoming the primary screen unless selected or visible according to the model.
- Custom scene layouts can override primary route selection through `primaryRouteSelector`.
- Unit tests cover single-stack context migration, multi-stack context, selected stack switching, visible entries across stacks, stack name extraction, and primary route selection.
