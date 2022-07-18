import * as ss from 'simple-statistics'
import * as assert from 'assert'
import { AppInfo } from '../src/appinfo'

const appsUnderTest = driver.config.customApps as AppInfo[]
const sleepTimeMs = 100

describe('Apps', () => {
    // install apps and collect their startup times
    before(async () => {
        const runs = driver.config.startupRuns as number

        for (var j = 0; j < appsUnderTest.length; j++) {
            const app = appsUnderTest[j]

            // sleep before the first test to improve the first run time
            await new Promise(resolve => setTimeout(resolve, 1000))

            console.log(`Collecting startup times for app ${app.name}`)
            for (var i = 0; i < runs; i++) {
                // Note: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
                await driver.startActivity(app.name, app.activity)

                // kill the app and sleep before running the next iteration
                await driver.terminateApp(app.name)
                await new Promise(resolve => setTimeout(resolve, sleepTimeMs))
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
            console.log(`App ${app.name} launch times mean: ${ss.mean(app.startupTimes)} ms | stddev: ${ss.standardDeviation(app.startupTimes).toFixed(2)}`)
            app.startupTimes = filterOutliers(app.startupTimes)
            console.log(`App ${app.name} launch times (filtered): [${app.startupTimes}]`)
            console.log(`App ${app.name} launch times (filtered) mean: ${ss.mean(app.startupTimes)} ms | stddev: ${ss.standardDeviation(app.startupTimes).toFixed(2)}`)
        }

        if (appsUnderTest.length == 2) {
            const time0 = ss.mean(appsUnderTest[0].startupTimes)
            const time1 = ss.mean(appsUnderTest[1].startupTimes)
            const diff = time1 - time0
            console.log(`App ${appsUnderTest[1].name} takes approximately ${Math.abs(diff).toFixed(2)} ms ${diff >= 0 ? 'more' : 'less'} time to start than app ${appsUnderTest[0].name}`)
        }
    })
})

// See https://en.wikipedia.org/wiki/Interquartile_range#Outliers for details
function filterOutliers(list: number[]): number[] {
    // sort array (as numbers)
    list.sort((a, b) => a - b)

    const Q1 = ss.quantileSorted(list, .25)
    const Q3 = ss.quantileSorted(list, .75)
    const IQR = Q3 - Q1;

    const between = (num: number, a: number, b: number) => num >= a && num <= b

    return list.filter(num => between(num, Q1 - 1.5 * IQR, Q3 + 1.5 * IQR))
}