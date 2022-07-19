import { updateConfig } from './wdio.android.shared.conf';
import config from './wdio.shared.sauce.conf';

updateConfig(config)

const env = process.env

config.capabilities = [{
    platformName: 'Android',
    'appium:automationName': 'UIAutomator2',
    'appium:disableWindowAnimation': true,
    'appium:deviceName': 'Google Pixel 4 XL', // Android 10 (API), 	ARM | octa core | 1785 MHz
    // Pixel 4 XL currently has three devices, one on each Android 10, 11, 12
    // 'appium:platformVersion': '11',
    'sauce:options': {
        name: 'Performance tests',
        build: env.CI == undefined
            ? `Local build ${new Date().getTime()}`
            : `CI ${env.GITHUB_REPOSITORY} ${env.GITHUB_REF} ${env.GITHUB_RUN_ID}`,
        tags: ['android', env.CI == undefined ? 'local' : 'ci']
    },
}];

exports.config = config;