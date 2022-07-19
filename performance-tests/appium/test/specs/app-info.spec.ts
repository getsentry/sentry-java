import * as ss from 'simple-statistics'
import * as assert from 'assert'
import { AppInfo } from '../src/appinfo'
import * as fs from 'fs'
import { sprintf } from 'sprintf-js';
import * as bytes from 'bytes';


const appsUnderTest = driver.config.customApps as AppInfo[]
const sleepTimeMs = 300
const MiB = 1024 * 1024
const printf = (...args: any[]) => console.log(sprintf(...args))
const logAppPrefix = `App %-${ss.max(appsUnderTest.map(a => a.name.length))}s`

describe('App info', () => {
    // install apps and collect their startup times
    before(async () => {
        const runs = driver.config.startupRuns as number

        for (var j = 0; j < appsUnderTest.length; j++) {
            const app = appsUnderTest[j]

            // sleep before the first test to improve the first run time
            await new Promise(resolve => setTimeout(resolve, 1000))

            printf(`${logAppPrefix} collecting startup times`, app.name)
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

    it('startup times', async () => {
        for (const app of appsUnderTest) {
            printf(`${logAppPrefix} launch times (original) | mean: %3.2f ms | stddev: %3.2f | values: [${app.startupTimes}]`,
                app.name, ss.mean(app.startupTimes), ss.standardDeviation(app.startupTimes))
            app.startupTimes = filterOutliers(app.startupTimes)
            printf(`${logAppPrefix} launch times (filtered) | mean: %3.2f ms | stddev: %3.2f | values: [${app.startupTimes}]`,
                app.name, ss.mean(app.startupTimes), ss.standardDeviation(app.startupTimes))
            expect(ss.standardDeviation(app.startupTimes)).toBeLessThan(50)
        }

        if (appsUnderTest.length == 2) {
            const time0 = ss.mean(appsUnderTest[0].startupTimes)
            const time1 = ss.mean(appsUnderTest[1].startupTimes)
            const diff = time1 - time0
            printf(`${logAppPrefix} takes approximately %3d ms %s time to start than app %s`,
                appsUnderTest[1].name, Math.abs(diff), diff >= 0 ? 'more' : 'less', appsUnderTest[0].name)

            // fail if the slowdown is not within the expected range
            expect(diff).toBeGreaterThan(0)
            expect(diff).toBeLessThan(150)
        }
    })

    it('binary size', async () => {
        for (const app of appsUnderTest) {
            printf(`${logAppPrefix} size is %s`, app.name, bytes(fs.statSync(app.path).size))
        }

        if (appsUnderTest.length == 2) {
            const value0 = fs.statSync(appsUnderTest[0].path).size
            const value1 = fs.statSync(appsUnderTest[1].path).size
            const diff = value1 - value0
            printf(`${logAppPrefix} is %s %s than app %s`,
                appsUnderTest[1].name, bytes(Math.abs(diff)), diff >= 0 ? 'larger' : 'smaller', appsUnderTest[0].name)

            // fail if the added size is not within the expected range
            expect(diff).toBeGreaterThan(1.8 * MiB)
            expect(diff).toBeLessThan(2.1 * MiB)
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