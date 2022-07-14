import * as path from 'path'
import { AppInfo } from './appinfo';
import config from './wdio.shared.local.appium.conf';

config.capabilities = [{
    platformName: 'Android',
    'appium:automationName': 'UIAutomator2',
    'appium:autoLaunch': false,
}];

config.customApps = [
    new AppInfo(
        'io.sentry.java.tests.perf.appplain',
        'MainActivity',
        path.join(process.cwd(), '../test-app-plain/app/build/outputs/apk/release/app-release.apk')
    ),
    new AppInfo(
        'io.sentry.java.tests.perf.appsentry',
        'MainActivity',
        path.join(process.cwd(), '../test-app-sentry/app/build/outputs/apk/release/app-release.apk')
    ),
]

exports.config = config;