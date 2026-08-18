# Idea Spec

## Problem

Sentry Buddy needs a realistic Android flow playground that exercises the signals it records: screens, UI actions, breadcrumbs, errors, transactions, HTTP spans, database spans, and custom application spans. The current sample app has useful primitives, but no polished, cohesive Compose mini-app with enough navigation depth and user-triggered work to validate Buddy recordings end-to-end.

## Target Users

Android SDK engineers validating Sentry Buddy recording quality during development.

QA and release reviewers who need repeatable sample flows that generate rich traces and events.

Demo users who need a visually credible app surface for showing Sentry Buddy without exposing internal test scaffolding.

Future contributors who need an obvious place to add Buddy-focused sample scenarios.

## Solution Overview

Add a new Compose-based “Sentry Travel” playground to the Android sample app.

Place it under the sample app's Integrations section as the first item. Selecting it launches `SentryBuddyActivity`.

The mini-app should feel like a small travel app rather than a test menu. It should include a polished travel-themed UI with destination cards, trip planning surfaces, booking states, itinerary details, and action-driven feedback.

Core navigation tree:

- Integrations
- Sentry Travel entry
- SentryBuddyActivity
- Travel Home
- Explore Destinations
- Destination Details
- Hotel / Stay Details
- Booking Review
- Booking Confirmation
- My Trips
- Trip Details
- Day Itinerary
- Activity Details
- Traveler Profile
- Support / Help

Primary flows:

- Browse featured destinations from Travel Home.
- Search destinations and open a destination details screen.
- Load availability or recommendations from destination details.
- Pick a stay or itinerary option.
- Continue to booking review.
- Confirm a booking.
- View the saved trip from My Trips.
- Open itinerary day details and activity details.
- Edit traveler preferences in profile.
- Trigger a support action or simulated failure for error-event validation.

Signal generation:

- HTTP spans from explicit user actions such as “Check availability,” “Refresh deals,” “Load recommendations,” and “Contact support.”
- Database spans from saving bookings, reading saved trips, updating traveler preferences, and caching destination metadata, reusing or mirroring the existing SQLite sample infrastructure where practical.
- Custom application spans around recommendation scoring, itinerary generation, price calculation, booking validation, image/list hydration, and profile preference matching.
- Breadcrumbs from meaningful user actions: search submitted, destination opened, stay selected, booking reviewed, booking confirmed, itinerary opened, support contacted.
- Transactions/screens from the Compose navigation tree, so Buddy can show a non-trivial screen sequence.
- Optional controlled error action such as “Simulate booking failure,” clearly labeled as a test action and useful for validating Buddy error capture.

Implementation shape:

- Add `SentryBuddyActivity` as a Compose activity in the Android sample app.
- Register it in `AndroidManifest.xml`.
- Add the Integrations list item in `MainActivity.kt` as the first item.
- Keep the Sentry Travel UI self-contained under the sample app source tree, likely near existing Compose sample code.
- Reuse existing sample app styling where required, but give the playground a distinct polished theme: travel imagery placeholders, cards, gradients or strong visual hierarchy, itinerary timelines, and clear primary actions.
- Reuse existing SQLite helper/sample code if it is already suitable; otherwise add minimal local persistence dedicated to the travel playground.
- Prefer deterministic mocked travel data. Network calls should be safe, bounded, and resilient to offline use.

## Key Constraints

- Must live in the Android sample app under the provided paths.
- Must be reachable from Integrations as the first item.
- Must launch `SentryBuddyActivity`.
- Must be Compose-based.
- Must generate meaningful Sentry Buddy test data through user actions, not only automatic startup work.
- Must exercise a deep enough navigation tree to validate screen ordering and flow recording.
- Must create HTTP spans, database spans, and other application spans.
- Must not require real travel APIs, credentials, paid services, or unstable backend dependencies.
- Must degrade gracefully when offline or when HTTP test endpoints fail.
- Must avoid broad SDK behavior changes outside the sample app.
- Must avoid adding unnecessary new dependencies unless existing sample app dependencies cannot support the required UI or spans.
- Must keep test actions understandable and safe for demos.

## Out of Scope

- Real travel search, booking, payment, account creation, maps, authentication, or external vendor integrations.
- Changing Sentry Buddy recording behavior.
- Changing SDK public APIs.
- Building a reusable travel app framework.
- Persisting real user data.
- Adding complex image loading, remote configuration, or production-grade caching.
- Supporting tablets, foldables, or advanced adaptive layouts beyond reasonable Compose responsiveness.
- Perfect visual parity with any external travel product.

## Open Questions

- Should HTTP spans use existing sample infrastructure, a public test endpoint, or a small in-app/mockable client wrapper that still exercises instrumented HTTP code?
- Is `SentryBuddyActivity` already present on the integration branch, or should this task create it?
- Should the simulated failure capture only an exception event, or should it also include a failed transaction/span path?
- Should the playground include session replay-specific gestures, such as scroll-heavy itinerary views and text input, or focus strictly on Buddy's existing trace/event/screen capture work?
- Are there existing sample app design components that must be reused, or can Sentry Travel define its own small visual system?

## Success Criteria

- Sentry Travel appears first in the Android sample app Integrations list.
- Opening Sentry Travel launches `SentryBuddyActivity`.
- The mini-app presents a polished Compose travel experience, not a plain debug menu.
- A reviewer can record a Buddy flow that visits at least six distinct screens.
- A reviewer can trigger at least one HTTP span from a visible user action.
- A reviewer can trigger at least one database span from a visible user action.
- A reviewer can trigger at least one custom application span from a visible user action.
- A reviewer can optionally trigger an error event from a clearly labeled action.
- Generated breadcrumbs and screen names are meaningful enough to understand the recorded flow.
- The sample app remains stable when offline.
- The change is isolated to sample app code except for unavoidable manifest/build wiring.
