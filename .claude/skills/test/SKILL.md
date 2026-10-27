---
name: test
description: Run tests for a specific SDK module. Use when asked to "run tests", "test module", "run unit tests", "run integration tests", "run e2e tests", or test a specific class. Auto-detects unit tests vs integration test suites. Supports interactive mode.
allowed-tools: Bash, Read, Glob, AskUserQuestion
argument-hint: [interactive] <module-name-or-file-path> [test-class-filter]
---

# Run Tests

Run tests for a specific module. Auto-detects whether to run unit tests or an integration test
suite.

## Step 0: Check for Interactive Mode

If `$ARGUMENTS` starts with `interactive` (e.g., `/test interactive sentry ScopesTest`), enable interactive mode. Strip the `interactive` keyword from the arguments before proceeding.

In interactive mode, use AskUserQuestion at decision points as described in the steps below.

## Step 1: Parse the Argument

The argument can be either:
- A **file path** (e.g., `@sentry/src/test/java/io/sentry/ScopesTest.kt`)
- A **module name** (e.g., `sentry-android-core`, `sentry-spring-boot-4`)
- A **module name + test filter** (e.g., `sentry ScopesTest`)

Extract the module name and optional test class filter from the argument.

**Interactive mode:** If the test filter is ambiguous (e.g., matches multiple test classes across modules), use AskUserQuestion to let the user pick which test class(es) to run.

## Step 2: Detect Test Type

| Signal | Test Type |
|--------|-----------|
| Path contains `/src/integrationTest*/` or `/src/agentFixture/` | Integration test suite |
| Path contains `sentry-samples/`, or module name starts with `sentry-samples-` | None: samples have no tests |
| Everything else | Unit test |

For a sample, tell the user that samples have no tests, and that the end-to-end tests for what
the sample shows live in the module it uses (see the `e2e_tests` rule).

## Step 3a: Run Unit Tests

Determine the Gradle test task:

| Module Pattern | Test Task |
|---------------|-----------|
| `sentry-android-*` | `testReleaseUnitTest` |
| `sentry-compose*` | `testReleaseUnitTest` |
| `*-android` | `testReleaseUnitTest` |
| Everything else | `test` |

**Interactive mode:** Before running, read the test class file and use AskUserQuestion to ask:
- "Run all tests in this class, or a specific method?" — list the test method names as options.

If the user picks a specific method, use `--tests="*ClassName.methodName"` as the filter.

With a test class filter:
```bash
./gradlew ':<module>:<task>' --tests="*<filter>*" --info
```

Without a filter:
```bash
./gradlew ':<module>:<task>' --info
```

## Step 3b: Run an Integration Test Suite

The suite's task is named after its source set directory: `src/integrationTestWebflux/...` runs
with `integrationTestWebflux`. The OpenTelemetry agent suite runs in two tasks, one per init mode,
so for `src/integrationTestOtelAgent/` or `src/agentFixture/` run both:

```bash
./gradlew ':<module>:integrationTestOtelAgent' ':<module>:integrationTestOtelAgentWithoutAutoInit'
```

Otherwise, with or without a test class filter:

```bash
./gradlew ':<module>:<suite>' --tests="*<filter>*"
```

## Step 4: Report Results

Summarize the test outcome:
- Total tests run, passed, failed, skipped
- For failures: show the failing test name and the assertion/error message
