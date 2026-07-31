# PRD: Nav3 Multiple Backstacks

Status: ready-for-agent

## Problem Statement

Android developers using Sentry's Navigation 3 integration need accurate breadcrumbs, screen tracking, transaction names, and crash context when their app uses multiple retained Navigation 3 back stacks. Navigation 3 makes navigation state app-owned, so a multiple-stack app commonly has a selected top-level route and a map of retained back stacks rather than a single controller-owned stack.

The current draft integration covers single-stack navigation and multipane visibility, but it does not model retained inactive stacks or recipe-style multi-stack rendering where more than one stack can contribute entries to one display. Without explicit support, Sentry events either lose useful retained-stack context or require app developers to flatten their state into an inaccurate single-stack shape.

## Solution

Add first-class multiple-backstack support to the Navigation 3 integration while migrating the unreleased single-stack and multipane draft behavior to one unified navigation context model.

The integration will report navigation context as a plural model from the first release:

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

Single-stack apps use the same model with a default stack name:

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

The public API will let apps pass their selected stack, stack snapshots, stacks-in-use, stack name extraction, destination name extraction, optional argument extraction, and optional primary route selection. Switching the selected stack is treated as user-visible navigation: it emits a breadcrumb, updates screen tracking, and starts a navigation transaction when tracing is enabled.

## User Stories

1. As an Android developer using a single Nav3 back stack, I want Sentry to continue recording navigation breadcrumbs, so that my existing single-stack integration behavior remains useful.
2. As an Android developer using a single Nav3 back stack, I want Sentry context to use the same shape as multi-stack apps, so that payloads are consistent from the first release.
3. As an Android developer using bottom navigation with multiple retained stacks, I want Sentry to include every retained stack in crash context, so that I can understand how a user reached the current app state.
4. As an Android developer using bottom navigation, I want Sentry to identify the selected stack, so that the current app section is clear in events.
5. As an Android developer using recipe-style start-plus-selected stack rendering, I want Sentry to list stacks in use separately from all retained stacks, so that event context distinguishes rendered stacks from retained background stacks.
6. As an Android developer using multipane scenes, I want Sentry to list visible entries, so that events show all routes currently rendered on screen.
7. As an Android developer using multiple stacks and multipane scenes together, I want visible entries to include their stack names, so that routes from different stacks are not ambiguous.
8. As an Android developer, I want to customize stack names, so that Sentry context contains readable stable stack identifiers instead of noisy object strings.
9. As an Android developer, I want to customize route names, so that Sentry transaction names and breadcrumbs use product terminology instead of implementation class names.
10. As an Android developer, I want route arguments to remain opt-in, so that I do not accidentally send PII or secrets to Sentry.
11. As an Android developer who opts into route arguments, I want arguments attached consistently to breadcrumbs, transactions, backstacks, and visible entries, so that navigation data is coherent.
12. As an Android developer using custom scene metadata, I want to override primary route selection, so that Sentry chooses the screen my app considers primary.
13. As an Android developer using Material adaptive scenes, I want a primary-route escape hatch, so that Sentry does not depend on brittle assumptions about Material metadata internals.
14. As an Android developer switching top-level tabs, I want Sentry to record the switch as a navigation event, so that breadcrumbs and performance data match user-perceived navigation.
15. As an Android developer mutating an inactive retained stack, I want Sentry to update crash context without incorrectly changing the current screen, so that background retained state does not pollute foreground navigation.
16. As an Android developer with tracing enabled, I want selected-stack changes and active-stack destination changes to start navigation transactions, so that navigation performance remains observable.
17. As an Android developer with tracing disabled, I want Sentry to continue starting a new trace where appropriate without starting navigation transactions, so that trace propagation behavior remains consistent.
18. As an Android developer using screen tracking, I want Sentry to update `scope.screen` from the selected primary route, so that events are attributed to the current screen.
19. As an Android developer using multipane layouts, I want `contexts.app.view_names` to include all visible route names, so that events capture multi-view UI state.
20. As an SDK maintainer, I want the state model tested independently from Compose, so that complex stack and primary-route behavior is easy to validate.
21. As an SDK maintainer, I want lightweight Compose tests for public overload wiring and disposal, so that the integration works from user-facing APIs.
22. As an SDK maintainer, I want API dump updates generated rather than edited manually, so that binary compatibility tracking remains correct.
23. As an SDK maintainer, I want docs and examples updated, so that users can wire app-owned Nav3 multiple-stack state without guessing.

## Implementation Decisions

- Use the recommended four-part implementation split: navigation context model, multiple-stack public API, visibility/primary selection, and docs/API surface.
- Migrate the draft single-stack context from a singular backstack shape to the plural model before the Navigation 3 module is released.
- Represent selected navigation state with `selected_stack`, not `active_stack`, to avoid ambiguity with rendered or retained stacks.
- Represent currently displayed stack participation with `stacks_in_use`.
- Represent all retained stacks with `backstacks`, where each stack entry includes `name`, `selected`, `in_use`, and capped `backstack` route entries.
- Represent currently rendered entries with `visible_entries` rather than `visible` or `scene`, because this field captures rendered entry routes, not Nav3 scene identity.
- Include `stack` on visible entries when stack ownership is known.
- Add a `stackNameExtractor` callback. The default should derive a readable route-like name from the stack key.
- Keep `nameExtractor` for destination route names and `argumentsExtractor` for opt-in arguments.
- Add a `primaryRouteSelector` callback for custom and Material scene layouts. The default policy should prefer visible entries from the selected stack and fall back conservatively when metadata is inconclusive.
- Treat selected-stack changes as navigation events that can emit breadcrumbs, update screen tracking, and start transactions.
- Treat inactive retained stack changes as context updates unless they affect the selected or visible route.
- Keep instrumentation guarded so throwing key methods or throwing user callbacks cannot crash the host app.
- Keep the integration generic and app-state driven. It should support Navigation 3 recipe shapes without depending on a concrete recipe navigation-state class.
- Update documentation and examples to show both single-stack default context and explicit multiple-stack wiring.

The prototype validated the core payload shape:

```json
{
  "navigation": {
    "selected_stack": "mail",
    "stacks_in_use": ["home", "mail"],
    "backstacks": [
      { "name": "home", "selected": false, "in_use": true, "backstack": [{ "route": "/Home" }] },
      { "name": "mail", "selected": true, "in_use": true, "backstack": [{ "route": "/Inbox" }] }
    ],
    "visible_entries": [
      { "stack": "home", "route": "/Home" },
      { "stack": "mail", "route": "/Inbox" }
    ]
  }
}
```

## Testing Decisions

- Test external behavior, not implementation details. Assertions should verify breadcrumbs, transactions, scope screen, app view names, and navigation context payloads.
- Add pure holder/model tests for single-stack context migration, multi-stack context, stack-name extraction, selected-stack switching, inactive stack updates, stack ownership in visible entries, and primary route selection.
- Add Compose tests for public overload wiring, recomposition stability, disposal cleanup, and multi-stack effect observation.
- Update existing single-stack and multipane context tests to assert the unified plural context shape.
- Keep existing safety tests for throwing extractors and hostile key methods, and extend them to new stack name and primary route callbacks.
- Cover privacy behavior by verifying arguments remain absent unless an argument extractor is provided.
- Cover max backstack size behavior for each retained stack.
- Run the module unit tests after implementation. Run formatting and API dump generation because the feature changes public API.

## Out of Scope

- Owning, mutating, or replacing app navigation state.
- Deep-link parsing, synthetic backstack construction, or route matching.
- Full Nav3 scene identity reporting.
- Automatic PII scrubbing for extracted route arguments.
- Support for remote issue creation, pull request creation, or remote tracker updates as part of make-it.

## Further Notes

- The Android developer site failed to fetch from this environment, but nav3-recipes raw sources were accessible and sufficient to validate the app-owned multi-stack model.
- Navigation 3 recipe metadata is intentionally flexible. Default heuristics are useful, but `primaryRouteSelector` is the reliable escape hatch for app-specific scene semantics.
- Because the Navigation 3 module is unreleased, the PRD intentionally changes the draft Phase 1/2 context model before first public release instead of preserving a short-lived singular context shape.

## Comments
