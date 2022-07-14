import * as ss from 'simple-statistics'
import * as assert from 'assert'


const appName = 'com.saucelabs.mydemoapp.rn'
const activityName = 'MainActivity'

describe('App', () => {
    it('starts quickly', async () => {
        const runs = 10
        for (var i = 0; i < runs; i++) {
            if (i > 0) {
                // kill the app and sleep before running the next iteration
                driver.terminateApp(appName)
                await new Promise(resolve => setTimeout(resolve, 1000))
            }

            // NOTE: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
            await driver.startActivity(appName, activityName)
        }

        const events = await driver.getEvents([])
        const startupTimes = events.commands
            .filter((cmd: any) => cmd.cmd == 'startActivity')
            .map((cmd: any) => cmd.endTime - cmd.startTime)

        assert.equal(startupTimes.length, runs)

        console.log(`App launch times: [${startupTimes}]`)
        console.log(`App launch mean: ${ss.mean(startupTimes)} ms | stddev: ${ss.standardDeviation(startupTimes).toFixed(2)}`)
    })
})
