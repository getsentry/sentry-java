import { SauceRegions } from '@wdio/types/build/Options';
import { config } from './wdio.shared.conf';


/**
 * Sauce Service Providers
 */
config.user = process.env.SAUCE_USERNAME;
config.key = process.env.SAUCE_ACCESS_KEY;
config.region = (process.env.SAUCE_REGION || 'us') as SauceRegions;

/**
 * Services
 */
config.services = config.services.concat([['sauce']]);

export default config;