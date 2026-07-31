# Add retained-tab multi-backstack demo and final verification

Status: done
Type: AFK
Blocked by: 03

## Parent

.scratch/nav3-sample-app/PRD.md

## What to build

Complete the Navigation Activity by adding a tabbed multi-backstack scenario. Each tab should retain its own stack, support independent route pushes, and switch the selected stack without losing inactive tab history. Wire this scenario through the multiple-stack `SentryNav3NavigationEffect` overload.

After the scenario is implemented, run final sample compile/build verification and make any small adjustments required to keep the Activity understandable and maintainable.

## Acceptance criteria

- [ ] The sample includes at least two tabs backed by separate retained backstacks.
- [ ] Switching tabs updates the selected stack without clearing inactive stacks.
- [ ] Each tab can push an independent detail route.
- [ ] The multi-backstack scenario uses the multiple-stack `SentryNav3NavigationEffect` overload.
- [ ] The UI makes selected stack and retained stack state easy to inspect manually.
- [ ] The Android sample app compile/build verification passes for the implemented sample.
- [ ] The implementation does not change Nav3 SDK public APIs.

## Blocked by

- 03

## Comments
