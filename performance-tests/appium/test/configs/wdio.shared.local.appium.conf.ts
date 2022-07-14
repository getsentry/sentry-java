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

export default config;