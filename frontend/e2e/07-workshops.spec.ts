import { expect, NEW_PASSWORD, test, uid } from './support/bayflow';

/**
 * SaaS phase 4: workshops sign themselves up on the public site, get their own address
 * (<slug>.localhost) and run on their own, with nobody touching the database.
 */
test.describe('Workshops on their own addresses', () => {
  test('two workshops sign up, configure themselves and stay independent', async ({ cast }) => {
    const slugA = `e2e-a-${uid().toLowerCase()}`;
    const slugB = `e2e-b-${uid().toLowerCase()}`;
    const ownerPasswordA = NEW_PASSWORD;
    const ownerPasswordB = `${NEW_PASSWORD}-b`;

    // ---- Workshop A signs up on the public site -----------------------------------------
    const ownerA = await cast.actor('Owner of workshop A', { at: cast.siteURL });
    await ownerA.say('Opens the Ramplight site and sets up a workshop');
    await ownerA.page.getByRole('link', { name: 'Set up your workshop' }).first().click();
    const form = ownerA.page.getByRole('form', { name: 'Set up your workshop' });
    await form.getByLabel('Workshop name').fill(`Alpha Autos ${slugA}`);
    await form.getByLabel('Workshop address').fill(slugA);
    await expect(form.getByText(`${slugA}.localhost is available`)).toBeVisible();
    await form.getByLabel('Currency').selectOption('EUR');
    await form.getByLabel('Time zone').selectOption('Europe/Berlin');
    await form.getByLabel('Username').fill('owner');
    await form.getByLabel('Email').fill('owner@alpha.example');
    await form.getByLabel('Password', { exact: true }).fill(ownerPasswordA);
    await form.getByLabel('Confirm password').fill(ownerPasswordA);
    await form.getByRole('button', { name: 'Create workshop' }).click();

    await expect(ownerA.page).toHaveURL(new RegExp(`^${cast.workshopURL(slugA)}/login\\?welcome=1`));
    await expect(ownerA.page.getByText('Your workshop is ready')).toBeVisible();
    await expect(ownerA.page.getByTestId('workshop-name')).toHaveText(`Alpha Autos ${slugA}`);
    await ownerA.check(`Workshop A is live at ${slugA}.localhost`);

    await ownerA.signIn('owner', ownerPasswordA);
    await ownerA.page.goto(`${cast.workshopURL(slugA)}/settings`);
    const settings = ownerA.page.getByRole('form', { name: 'Workshop settings' });
    await expect(settings.getByLabel('Currency')).toHaveValue('EUR');
    await settings.getByLabel('Labour rate per hour').fill('120');
    await settings.getByRole('button', { name: 'Save settings' }).click();
    await ownerA.expectToast('Workshop settings saved');
    await ownerA.check('Sets its own labour rate; EUR and Berlin time from sign-up');

    // ---- Workshop B signs up, with the same admin username ------------------------------
    const ownerB = await cast.actor('Owner of workshop B', { at: cast.siteURL });
    await ownerB.page.goto('/signup');
    const formB = ownerB.page.getByRole('form', { name: 'Set up your workshop' });
    await formB.getByLabel('Workshop name').fill(`Bravo Garage ${slugB}`);

    await ownerB.edge("Tries workshop A's address first");
    await formB.getByLabel('Workshop address').fill(slugA);
    await expect(formB.getByText(`${slugA}.localhost is already taken`)).toBeVisible();
    await expect(formB.getByRole('button', { name: 'Create workshop' })).toBeDisabled();

    await ownerB.edge('Then a reserved address');
    await formB.getByLabel('Workshop address').fill('admin');
    await expect(formB.getByText('admin is reserved. Choose another address.')).toBeVisible();

    await formB.getByLabel('Workshop address').fill(slugB);
    await expect(formB.getByText(`${slugB}.localhost is available`)).toBeVisible();
    await formB.getByLabel('Username').fill('owner');
    await formB.getByLabel('Email').fill('owner@bravo.example');
    await formB.getByLabel('Password', { exact: true }).fill(ownerPasswordB);
    await formB.getByLabel('Confirm password').fill(ownerPasswordB);
    await formB.getByRole('button', { name: 'Create workshop' }).click();
    await expect(ownerB.page).toHaveURL(new RegExp(`^${cast.workshopURL(slugB)}/login`));

    await ownerB.edge("Signs in with workshop A's owner password");
    await ownerB.page.getByLabel('Username').fill('owner');
    await ownerB.page.getByLabel('Password').fill(ownerPasswordA);
    await ownerB.page.getByRole('button', { name: 'Sign in' }).click();
    await expect(ownerB.page.getByText('Username or password is incorrect.')).toBeVisible();
    await ownerB.signIn('owner', ownerPasswordB);
    await ownerB.page.goto(`${cast.workshopURL(slugB)}/settings`);
    await expect(
      ownerB.page.getByRole('form', { name: 'Workshop settings' }).getByLabel('Labour rate per hour'),
    ).not.toHaveValue('120');
    await ownerB.check("Same username, separate accounts; A's settings are not B's");

    // ---- Sessions never cross workshops --------------------------------------------------
    const apiA = await cast.api('owner', ownerPasswordA, cast.workshopURL(slugA));
    const atOwnAddress = await apiA.raw('GET', '/api/v1/auth/me');
    expect(atOwnAddress.status).toBe(200);
    const atOtherAddress = await apiA.raw('GET', `${cast.workshopURL(slugB)}/api/v1/auth/me`);
    expect(atOtherAddress.status).toBe(401);
    expect(atOtherAddress.code).toBe('workshop-mismatch');
    const atOtherService = await apiA.raw('GET', `${cast.workshopURL(slugB)}/api/v1/appointments`);
    expect(atOtherService.status).toBe(401);
    await cast.note("Workshop A's token is refused at workshop B's address (401), in every service");
  });

  test('an address without a workshop says so', async ({ cast }) => {
    const visitor = await cast.actor('Visitor', { at: cast.workshopURL(`no-such-${uid().toLowerCase()}`) });
    await visitor.edge('Opens a workshop address that does not exist');
    await visitor.page.goto('/login');
    await expect(visitor.page.getByRole('heading', { name: 'Workshop not found' })).toBeVisible();
    await expect(visitor.page.getByLabel('Username')).toHaveCount(0);
    await visitor.page.getByRole('link', { name: 'Set up a workshop' }).click();
    await expect(visitor.page).toHaveURL(new RegExp(`^${cast.siteURL}/signup`));
    await visitor.check('Explains it and offers to set up a workshop');
  });
});
