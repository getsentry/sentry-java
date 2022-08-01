import { SauceLabsOptions } from 'saucelabs'
import { AppInfo } from '../src/appinfo'
import { config } from './wdio.shared.conf'
import { uploadApp } from '../src/sauce-utils'

const sauceOptions: SauceLabsOptions = {
    user: process.env.SAUCE_USERNAME!,
    key: process.env.SAUCE_ACCESS_KEY!,
    region: process.env.SAUCE_REGION || 'us'
}
config.sauceOptions = sauceOptions

config.user = sauceOptions.user
config.key = sauceOptions.key
config.region = sauceOptions.region

config.services = config.services.concat([['sauce']])

config.startupRuns = 50

config.onPrepare = async (config, capabilities) => {
    const appsUnderTest = config.customApps as AppInfo[]

    capabilities[0]['appium:otherApps'] = []
    for (const app of appsUnderTest) {
        const fileId = await uploadApp(sauceOptions, app)
        console.log(`Adding app ${app.name} from ${app.path} to 'appium:otherApps' as 'storage:${fileId}'`)
        capabilities[0]['appium:otherApps'].push(`storage:${fileId}`)
    }
}

export default config