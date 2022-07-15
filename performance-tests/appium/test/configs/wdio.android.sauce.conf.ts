import { updateConfig } from './wdio.android.shared.conf';
import config from './wdio.shared.sauce.conf';

updateConfig(config)

config.capabilities = [{
    platformName: 'Android',
    'appium:automationName': 'UIAutomator2',
    'appium:platformVersion': '11',
    // 'appium:deviceName': 'Android GoogleAPI Emulator',
    'appium:newCommandTimeout': 240,
    'sauce:options': {
        build: `Android Native Simple Example: build-${new Date().getTime()}`,
        // appiumVersion: '1.22.1',
    },
}];

exports.config = config;