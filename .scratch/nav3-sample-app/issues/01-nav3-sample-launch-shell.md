# Add Nav3 sample launch shell

Status: ready-for-agent
Type: AFK
Blocked by: None

## Parent

.scratch/nav3-sample-app/PRD.md

## What to build

Add the minimal shell for a Navigation 3 sample inside the existing Android sample app. The shell should make the sample reachable from the Tracing section and compile with the Nav3 integration dependency, but it does not need to implement all navigation scenarios yet.

This slice should keep the existing Android sample app structure. Add a new non-exported Navigation Activity, wire it into the manifest, add the Tracing screen launcher labeled “Open Navigation Activity”, and add the smallest dependency changes needed for the Activity to compile against the Nav3 integration and Navigation 3 runtime.

## Acceptance criteria

- [ ] The existing Android sample app has a Tracing section button labeled “Open Navigation Activity”.
- [ ] Tapping the button launches a new Navigation Activity.
- [ ] The Activity is registered as non-exported in the sample app manifest.
- [ ] The sample app depends on the Nav3 Sentry integration and Navigation 3 runtime.
- [ ] The Activity compiles with a minimal Compose screen and does not change Nav3 SDK public APIs.

## Blocked by

None - can start immediately

## Comments
