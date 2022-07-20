# Appium-based test

Originally based on [SauceLabs's demo scripts](https://github.com/saucelabs-training/demo-js/tree/8c936c7a6865823e3251362ed5cb8ed2327c59be/webdriverio/appium-app/examples/simple-example).

## Install dependencies

```shell
npm install
```

## Compile test apps

```shell
npm run build:apps
```

## Run tests

You can run your tests on Sauce Labs:

```shell
# Run Android Tests
npm run saucelabs:android
# Run iOS Tests
npm run saucelabs:ios
```

Or you can run the tests on your local Appium installation (head over to [appium.io](https://appium.io/) to get started):

```shell
# Run Android Tests
npm run local:android
# Run iOS Tests
npm run local:ios
```
