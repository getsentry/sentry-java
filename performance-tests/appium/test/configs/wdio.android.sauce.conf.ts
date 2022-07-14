import config from './wdio.shared.sauce.conf';
const buildName = `Android Native Simple Example: build-${new Date().getTime()}`;

config.capabilities = [{
    platformName: 'Android',
    'appium:platformVersion': '11',
    // 'appium:deviceName': 'Android GoogleAPI Emulator',
    'appium:automationName': 'UIAutomator2',
    'appium:autoLaunch': false,
    'appium:newCommandTimeout': 240,
    'sauce:options': {
        build: buildName,
        // appiumVersion: '1.22.1',
    },
}];

config.customApps = [
    new AppInfo('com.saucelabs.mydemoapp.rn', 'MainActivity', 'storage:filename=Android-MyDemoAppRN.apk')
]

exports.config = config;