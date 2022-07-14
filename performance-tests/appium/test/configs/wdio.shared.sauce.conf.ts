import {config} from './wdio.shared.conf';


/**
 * Sauce Service Providers
 */
config.user = process.env.SAUCE_USERNAME;
config.key = process.env.SAUCE_ACCESS_KEY;
config.region = process.env.REGION || 'us';

/**
 * Services
 */
config.services = config.services.concat([['sauce']]);

export default config;