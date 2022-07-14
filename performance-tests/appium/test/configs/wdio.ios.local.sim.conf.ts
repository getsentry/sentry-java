import {join} from  'path';
import config from './wdio.shared.local.appium.conf';

config.capabilities = [{
    platformName: 'iOS',
    'appium:platformVersion': '15.2',
    'appium:deviceName': 'iPhone Simulator',
    'appium:automationName': 'XCUITest',
    'appium:app': join(process.cwd(),'./test-apps/iOS-Simulator-MyRNDemoApp.zip'),
}];

exports.config = config;