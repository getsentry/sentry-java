# Appium-based test

## Compile test apps

```shell
./gradlew :sentry-android-integration-tests:test-app-plain:assembleRelease
./gradlew :sentry-android-integration-tests:test-app-sentry:assembleRelease
```

## Run tests

You can run your tests on Sauce Labs:

```shell
./gradlew :sentry-android-integration-tests:appium:test --tests StartupTestsAndroidSauce.*
```

Or you can run the tests on your local Appium installation (head over to [appium.io](https://appium.io/) to get started):

```shell
# run appium in another shell or detach
appium & 
# then start the test
./gradlew :sentry-android-integration-tests:appium:test --tests StartupTestsAndroidLocal.*
```

> Note: the local appium test sometimes fails to connect to session... just run it again.
