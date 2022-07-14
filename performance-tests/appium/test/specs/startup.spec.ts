import * as ss from 'simple-statistics'
import * as assert from 'assert'
import { AppInfo } from '../configs/appinfo'

const appsUnderTest = driver.config.customApps as AppInfo[]
const runs = 10

describe('Apps', () => {
    // install apps and collect their startup times
    before(async () => {
        for (var j = 0; j < appsUnderTest.length; j++) {
            const app = appsUnderTest[j]

            console.log(`Installing app ${app.name} from ${app.path}`)
            await driver.installApp(app.path)

            console.log(`Collecting startup times for app ${app.name}`)
            for (var i = 0; i < runs; i++) {
                // Note: sleeping before launching the app (instead of after), improves the speed of the first launch.
                await new Promise(resolve => setTimeout(resolve, 1000))

                // Note: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
                await driver.startActivity(app.name, app.activity)

                // kill the app and sleep before running the next iteration
                await driver.terminateApp(app.name)
            }

            const events = await driver.getEvents([])
            const offset = j * runs
            app.startupTimes = events.commands
                .filter((cmd: any) => cmd.cmd == 'startActivity')
                .map((cmd: any) => cmd.endTime - cmd.startTime)
                .slice(offset, offset + runs)

            assert.equal(app.startupTimes.length, runs)
        }
    })

    it('starts', async () => {
        for (const app of appsUnderTest) {
            console.log(`App ${app.name} launch times: [${app.startupTimes}]`)
            console.log(`App ${app.name} launch mean: ${ss.mean(app.startupTimes)} ms | stddev: ${ss.standardDeviation(app.startupTimes).toFixed(2)}`)
        }

        // TODO compare between the apps
    })
})
