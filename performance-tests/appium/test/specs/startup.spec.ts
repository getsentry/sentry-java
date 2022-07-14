const appName = 'com.saucelabs.mydemoapp.rn'
const activityName = 'MainActivity'

describe('App', () => {
    // TODO check impact of 'appium:disableWindowAnimation' setting
    it('starts quickly', async () => {
        const runs = 10
        for (var i = 0; i < runs; i++) {
            if (i > 0) {
                // kill the app and sleep before running the next iteration
                driver.terminateApp(appName)
                await new Promise(resolve => setTimeout(resolve, 1000))
            }

            // TODO check out .activateApp() - it should work on any OS.
            await driver.startActivity(appName, activityName)
        }

        const events = await driver.getEvents([])
        const startupTimes = new Times(events.commands
            .filter((cmd: any) => cmd.cmd == 'startActivity')
            .map((cmd: any) => cmd.endTime - cmd.startTime))

        console.log(`App launch times: [${startupTimes.items}]`)
        console.log(`App launch mean: ${startupTimes.mean} ms | stddev: ${startupTimes.stddev}`)
    })
})

class Times {
    items: number[]
    mean: number
    stddev: number

    constructor(items: number[]) {
        this.items = items
        const sum = items.reduce((a, b) => a + b, 0)
        this.mean = (sum / items.length) || 0
        this.stddev = Math.sqrt(items.map(x => Math.pow(x - this.mean, 2)).reduce((a, b) => a + b) / items.length)
    }
}
