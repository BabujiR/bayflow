import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, NEW_PASSWORD, test, uid } from './support/bayflow';

const FIXTURES = join(dirname(fileURLToPath(import.meta.url)), 'fixtures');

/**
 * Branding: a workshop's owner gives it its own logo, colour, sign-in photo and announcement,
 * and turns off "Powered by Bayflow"; its customers see that look, and no other workshop does.
 * Uses a newly signed-up workshop, so demo and acme keep their look.
 */
test.describe('Workshop branding', () => {
  test('an owner brands the workshop and customers see it', async ({ cast }) => {
    const slug = `e2e-brand-${uid().toLowerCase()}`;
    const name = `Crimson Garage ${slug}`;
    const bannerText = `Closed on 24 December (${slug})`;

    // ---- A new workshop, signed up through the public API ---------------------------------
    const site = await cast.anonymousApi(cast.siteURL);
    const signup = await site.post('/api/v1/tenants/signup', {
      headers: { 'X-Bayflow-Client': 'web' },
      data: {
        workshopName: name,
        slug,
        adminUsername: 'owner',
        adminEmail: `owner@${slug}.example`,
        adminPassword: NEW_PASSWORD,
      },
    });
    expect(signup.status(), 'workshop sign-up').toBe(201);
    const workshopURL = cast.workshopURL(slug);

    // ---- The owner brands it -------------------------------------------------------------
    const owner = await cast.actor('Workshop owner', { at: workshopURL });
    await owner.signIn('owner', NEW_PASSWORD);
    await owner.page.goto(`${workshopURL}/settings`);
    await owner.say('Opens Branding in the workshop settings');

    const logo = owner.page.getByTestId('branding-logo');
    await expect(logo.getByText('Bayflow logo shown')).toBeVisible();
    await logo.getByLabel('Upload logo').setInputFiles(join(FIXTURES, 'acme-logo.png'));
    await owner.expectToast('Logo saved');
    await expect(logo.getByRole('img', { name: 'Current logo' })).toBeVisible();

    const cover = owner.page.getByTestId('branding-cover');
    await cover
      .getByLabel('Upload sign-in cover photo')
      .setInputFiles(join(FIXTURES, 'workshop-cover.png'));
    await owner.expectToast('Sign-in cover photo saved');
    await owner.check('Logo and cover photo uploaded (checked and re-encoded by the server)');

    await owner.edge('Tries to upload a file that is not an image');
    await logo.getByLabel('Upload logo').setInputFiles({
      name: 'logo.png',
      mimeType: 'image/png',
      buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>'),
    });
    await expect(logo.getByRole('alert')).toHaveText('Upload a PNG or JPEG image.');

    const form = owner.page.getByRole('form', { name: 'Branding' });
    await form.getByLabel("Use my workshop's colour").check();
    await form.getByLabel('Colour code').fill('#be123c');
    await form.getByLabel('Message').fill(bannerText);
    await form.getByLabel('Style').selectOption('IMPORTANT');
    await form.getByLabel('Show "Powered by Bayflow"').uncheck();
    await form.getByRole('button', { name: 'Save branding' }).click();
    await owner.expectToast('Branding saved');
    await expect(owner.page.getByTestId('workshop-banner')).toHaveText(bannerText);
    await expect(owner.page.getByTestId('workshop-banner')).toHaveAttribute('data-variant', 'slim');
    await owner.check('Crimson colour, an announcement and no "Powered by Bayflow"');

    // ---- A customer sees the workshop's look ---------------------------------------------
    const customer = await cast.actor('Customer', { at: workshopURL });
    await customer.page.goto('/login');
    const shownLogo = customer.page.getByTestId('workshop-logo');
    await expect(shownLogo).toBeVisible();
    await expect(shownLogo).toHaveAttribute('alt', name);
    expect(await shownLogo.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
    await expect(customer.page.getByTestId('workshop-cover').locator('img')).toBeVisible();
    await expect(customer.page.getByTestId('workshop-banner')).toHaveText(bannerText);
    await expect(customer.page.getByTestId('powered-by')).toHaveCount(0);
    const primary = await customer.page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--primary').trim(),
    );
    expect(primary).toBe('#be123c');
    await expect(customer.page).toHaveTitle(name);
    await customer.check("The sign-in page shows the workshop's logo, photo, colour and announcement");

    const username = `brand${uid().toLowerCase()}`;
    await customer.registerCustomer(username, 'Casey Crimson');
    await expect(customer.page.getByTestId('workshop-banner')).toHaveAttribute('data-variant', 'full');
    await expect(customer.page.getByRole('link', { name: `${name} home` })).toBeVisible();
    await customer.check('Inside the app too, with the announcement in full');

    // ---- The images are public, cached and safe -------------------------------------------
    const visitor = await cast.anonymousApi(workshopURL);
    const current = (await (await visitor.get('/api/v1/tenants/current')).json()) as {
      branding: { logoUrl: string; coverUrl: string };
    };
    const image = await visitor.get(current.branding.logoUrl);
    expect(image.status()).toBe(200);
    expect(image.headers()['content-type']).toBe('image/png');
    expect(image.headers()['cache-control']).toContain('immutable');
    expect(image.headers()['x-content-type-options']).toContain('nosniff');
    const coverImage = await visitor.get(current.branding.coverUrl);
    expect(coverImage.headers()['content-type']).toBe('image/jpeg');
    await cast.note('Logo and cover are served at the workshop address with long-lived caching');

    // ---- No other workshop changes --------------------------------------------------------
    const elsewhere = await cast.anonymousApi(cast.baseURL);
    const other = (await (await elsewhere.get('/api/v1/tenants/current')).json()) as {
      branding?: { logoUrl: string | null; banner: { text: string } | null };
    };
    expect(other.branding?.banner?.text).not.toBe(bannerText);
    expect(other.branding?.logoUrl ?? null).not.toBe(current.branding.logoUrl);
    await cast.note("This run's own workshop keeps its own look");

    // ---- Back to Bayflow's look ----------------------------------------------------------------
    await owner.page.getByTestId('branding-logo').getByRole('button', { name: 'Remove' }).click();
    await owner.expectToast('Logo removed');
    await customer.page.goto('/');
    await customer.page.reload();
    await expect(customer.page.getByTestId('workshop-logo')).toHaveCount(0);
    await customer.check('Removing the logo brings back the Bayflow logo');
  });
});
