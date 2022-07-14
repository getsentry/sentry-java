import config from './wdio.shared.sauce.conf';
const buildName = `Android Emulator Native Simple Example: build-${new Date().getTime()}`;

config.capabilities = [{
    platformName: 'Android',
    'appium:platformVersion': '12.0',
    'appium:deviceName': 'Android GoogleAPI Emulator',
    'appium:automationName': 'UIAutomator2',
    // The name of the App in the Sauce Labs storage, for more info see
    // https://docs.saucelabs.com/mobile-apps/app-storage/
    'appium:app': 'storage:filename=Android.MyDemoAppRN.apk',
    'appium:appWaitActivity': 'com.saucelabs.mydemoapp.rn.MainActivity',
    'appium:newCommandTimeout': 240,
    'sauce:options': {
        build: buildName,
        appiumVersion: '1.22.1',
    },
}];

exports.config = config;