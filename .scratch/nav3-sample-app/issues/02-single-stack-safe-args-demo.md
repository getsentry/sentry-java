# Add single-stack and safe-argument demo

Status: done
Type: AFK
Blocked by: 01

## Parent

.scratch/nav3-sample-app/PRD.md

## What to build

Turn the Navigation Activity shell into a real single-stack Nav3 sample. The sample should own a small route backstack, render ordinary route changes, expose push/pop controls, and wire `SentryNav3NavigationEffect` at the same composition level as the Nav3 state owner and display.

Include a detail route with clearly safe demo arguments. Use `nameExtractor` for readable route names and `argumentsExtractor` only for non-PII sample values such as demo item IDs.

## Acceptance criteria

- [ ] The Navigation Activity contains a simple single-stack Nav3 flow.
- [ ] The UI shows enough current-route/backstack information to correlate manual taps with expected route names.
- [ ] The sample can push at least one detail route with a safe demo argument.
- [ ] The sample can pop routes without leaving the navigation state inconsistent.
- [ ] `SentryNav3NavigationEffect` is wired from the navigation root, not from an individual destination.
- [ ] Safe argument extraction is present and does not use realistic PII values.

## Blocked by

- 01

## Comments
