import { join } from 'path';
import config from './wdio.shared.local.appium.conf';

config.capabilities = [{
    platformName: 'Android',
    // 'appium:platformVersion': '11',
    // 'appium:deviceName': 'Android Emulator',
    'appium:automationName': 'UIAutomator2',
    'appium:app': join(process.cwd(), './test-apps/Android-MyDemoAppRN.apk'),
    'appium:autoLaunch': false,
}];

exports.config = config;