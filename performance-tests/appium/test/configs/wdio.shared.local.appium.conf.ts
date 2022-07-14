import {config} from './wdio.shared.conf';

config.services = config.services.concat([
    [
        'appium',
        {
            command: 'appium'
        }
    ]
]);

config.port = 4723;

export default config;