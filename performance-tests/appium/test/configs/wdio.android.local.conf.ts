import { updateConfig } from './wdio.android.shared.conf';
import config from './wdio.shared.local.appium.conf';

updateConfig(config)

config.capabilities = [{
    platformName: 'Android',
    'appium:automationName': 'UIAutomator2',
}];

exports.config = config;