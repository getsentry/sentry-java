import * as path from 'path'
import { AppInfo } from '../src/appinfo';

export function updateConfig(config: WebdriverIO.Config) {
    config.customApps = [
        new AppInfo(
            'io.sentry.java.tests.perf.appplain',
            'MainActivity',
            path.join(process.cwd(), '../test-app-plain/app/build/outputs/apk/release/app-release.apk')
        ),
        new AppInfo(
            'io.sentry.java.tests.perf.appsentry',
            'MainActivity',
            path.join(process.cwd(), '../test-app-sentry/build/outputs/apk/release/test-app-sentry-release.apk')
        ),
    ]
}