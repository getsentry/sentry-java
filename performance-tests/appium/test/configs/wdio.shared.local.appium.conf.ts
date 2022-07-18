import { config } from './wdio.shared.conf';

config.hostname = '[::1]';
config.port = 4723;

config.services = config.services.concat([
    [
        // local appium server startup command
        'appium',
        {
            command: 'appium',
            args: {
                address: config.hostname,
                port: config.port
            }
        }
    ]
]);

config.onPrepare = async (config, capabilities) => {
    const appsUnderTest = config.customApps as AppInfo[]

    capabilities[0]['appium:otherApps'] = []
    for (const app of appsUnderTest) {
        console.log(`Adding app ${app.name} from ${app.path} to 'appium:otherApps'`)
        capabilities[0]['appium:otherApps'].push(app.path)
    }

    capabilities[0]['appium:otherApps'] = JSON.stringify(capabilities[0]['appium:otherApps'])
}

export default config;