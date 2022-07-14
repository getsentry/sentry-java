export const config: WebdriverIO.Config = {
  // Please visit https://webdriver.io/docs/configurationfile/ for the full list of options

  // Specify what tests to run
  specs: ['./test/specs/*.spec.ts'],
  // Capabilities.. We will set these in the platform specific conf files
  capabilities: [],
  maxInstances: 1,
  //Test Configuration
  logLevel: 'info',
  baseUrl: '',
  waitforTimeout: 30000,
  // A timeout of 5 min
  connectionRetryTimeout: 5 * 60 * 1000,
  connectionRetryCount: 1,
  reporters: ['spec'],
  framework: 'mocha',
  mochaOpts: {
    ui: 'bdd',
    timeout: 60000,
  },
  services: [],
};
