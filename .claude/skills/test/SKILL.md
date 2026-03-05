---
name: test
description: Run tests for a specific SDK module. Use when asked to "run tests", "test module", "run unit tests", "run system tests", "run e2e tests", or test a specific class. Auto-detects unit vs system tests. Supports interactive mode.
allowed-tools: Bash, Read, Glob
argument-hint: [interactive] <module-name-or-file-path> [test-class-filter]
---

# Run Tests

Run tests for a specific module. Auto-detects whether to run unit tests or system tests.

## Step 0: Check for Interactive Mode

If `$ARGUMENTS` starts with `interactive` (e.g., `/test interactive sentry ScopesTest`), enable interactive mode. Strip the `interactive` keyword from the arguments before proceeding.

In interactive mode, use AskUserQuestion at decision points as described in the steps below.

## Step 1: Parse the Argument

The argument can be either:
- A **file path** (e.g., `@sentry/src/test/java/io/sentry/ScopesTest.kt`)
- A **module name** (e.g., `sentry-android-core`, `sentry-samples-spring-boot-4`)
- A **module name + test filter** (e.g., `sentry ScopesTest`)

Extract the module name and optional test class filter from the argument.

**Interactive mode:** If the test filter is ambiguous (e.g., matches multiple test classes across modules), use AskUserQuestion to let the user pick which test class(es) to run.

## Step 2: Detect Test Type

| Signal | Test Type |
|--------|-----------|
| Path contains `sentry-samples/` | System test |
| Module name starts with `sentry-samples-` | System test |
| Everything else | Unit test |

## Step 3a: Run Unit Tests

Determine the Gradle test task:

| Module Pattern | Test Task |
|---------------|-----------|
| `sentry-android-*` | `testDebugUnitTest` |
| `sentry-compose*` | `testDebugUnitTest` |
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

## Step 3b: Run System Tests

System tests require the Python-based test runner which manages a mock Sentry server and sample app lifecycle.

1. Ensure the Python venv exists:
```bash
test -d .venv || make setupPython
```

2. Extract the sample module name. For file paths like `sentry-samples/<sample-module>/src/...`, the sample module is the directory name (e.g., `sentry-samples-spring`).

3. Run the system test:
```bash
.venv/bin/python test/system-test-runner.py test --module <sample-module>
```

This starts the mock Sentry server, starts the sample app (Spring Boot/Tomcat/CLI), runs tests via `./gradlew :sentry-samples:<sample-module>:systemTest`, and cleans up afterwards.

## Step 4: Report Results

Summarize the test outcome:
- Total tests run, passed, failed, skipped
- For failures: show the failing test name and the assertion/error message
