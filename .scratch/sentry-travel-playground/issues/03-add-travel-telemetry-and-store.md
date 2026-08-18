# Add Sentry Travel telemetry and store actions

Status: done
Type: AFK
Blocked by: 02

## Description

Add the user-triggered data generation paths that make Sentry Travel useful for Buddy recordings.

Use existing `GithubAPI` for HTTP spans. Add a lightweight `TravelStore` backed by the sample app's existing SQLite infrastructure where practical. Add helper logic for custom child spans, breadcrumbs, and controlled exception capture.

## Acceptance criteria

- [ ] A visible action triggers an HTTP span and handles offline/failure gracefully.
- [ ] A visible action triggers a database span for saved trips or preferences.
- [ ] At least one visible action triggers a custom application span.
- [ ] Meaningful breadcrumbs are emitted for major user actions.
- [ ] A clearly labeled simulated failure captures an exception without crashing the activity.
- [ ] The sample app compiles.

## Comments
