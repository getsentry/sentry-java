
/**
 * This is a simple example of how to get started testing a native app
 */
describe('Simple Example Spec', () => {
    beforeEach(async () => {
        //Locator for products screen. If the session is android then use the android syntax otherwise ios
        const productScreenLocator = driver.isAndroid ? '//*[@content-desc="products screen"]' : 'id=products screen';

        //Wait for the application to start and load the initial screen (products screen)
        await $(productScreenLocator).waitForDisplayed();
    });

    it('should prompt the sort modal when I click the sort button', async () => {
        //Locator for sort button. If the session is android then use the android syntax otherwise ios
        const sortButtonLocator = driver.isAndroid ? '//*[@content-desc="sort button"]' : 'id=sort button';

        //Click on the sort button
        await $(sortButtonLocator).click();

        //Locator for sort modal. If the session is android then use the android syntax otherwise ios
        const sortModalLocator = driver.isAndroid ? '//*[@content-desc="active option"]' : 'id=active option';

        //Verify the sort modal is displayed on screen
        await expect($(sortModalLocator)).toBeDisplayed();
    });
});