Ui tests for Android
===========
Here will be put all ui tests for Android, running through Google's Espresso.
By default the envelopes sent to relay are caught by a mock server which allows us to check the envelopes sent.

# How to use

Simply run `./gradlew connectedCheck` to run all ui tests of all modules (requires a connected device, either physical or an emulator).
_Care: the benchmarks need to run the tests multiple times to get reliable results. This means they can take a long time (several minutes)._
If you don't care about benchmark tests you can run `./gradlew connectedCheck -x :sentry-uitest:sentry-uitest-android-benchmark:connectedCheck`.
You can run benchmark tests only with `./gradlew :sentry-uitest:sentry-uitest-android-benchmark:connectedCheck`.

# Troubleshooting

There is an issue with Android 11+ (Xiaomi only?).
In order to start an activity from the background (which the test orchestrator does internally), the app needs a special permission, which cannot be granted without user interaction.
To allow it on the device go in Settings -> apps -> manage apps -> Select the app, like `Sentry End2End Tests` -> Other permissions -> `Display pop-up windows while running in the background`.
The path may be different on other devices.
On older versions of Android there is no problem.

For this reason we cannot use the `testInstrumentationRunnerArguments["clearPackageData"] = "true"` in the build.gradle file, as clearing package data resets permissions.
This flag is used to run each test in its own instance of Instrumentation. This way they are isolated from one another and get their own Application instance.
More on https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner#enable-gradle 

