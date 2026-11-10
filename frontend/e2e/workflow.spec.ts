import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * The whole Bayflow workflow, through the UI, as three different people:
 * a new customer registers and books → the advisor confirms, marks arrived and checks in →
 * the advisor inspects and recommends work → the customer approves → the mechanic does the
 * work and completes the job → the customer sees it in the vehicle's service history.
 */

const DEMO_PASSWORD = 'Bayflow!2026';
const VIN_CHARS = 'ABCDEFGHJKLMNPRSTUVWXYZ0123456789';

const run = Date.now().toString(36).toUpperCase().slice(-6);
const customer = { username: `e2e-${run.toLowerCase()}`, password: 'E2e-Passw0rd!' };
const registration = `E2E ${run}`;
const vin = Array.from({ length: 17 }, () => VIN_CHARS[Math.floor(Math.random() * VIN_CHARS.length)]).join('');
const recommendation = `Replace front brake pads ${run}`;

/** Tomorrow at 10:00 in the browser's time zone. */
function tomorrowAtTen(): { date: string; dateTime: string } {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const pad = (n: number) => String(n).padStart(2, '0');
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return { date, dateTime: `${date}T10:00` };
}

async function signIn(browser: Browser, username: string, password = DEMO_PASSWORD): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: `Welcome, ${username}` })).toBeVisible();
  return page;
}

test('a car goes from booking to service history', async ({ browser }) => {
  const slot = tomorrowAtTen();

  // ---- Customer: register, complete the profile, add a vehicle, book ----------------------
  const customerPage = await (await browser.newContext()).newPage();
  await customerPage.goto('/register');
  await customerPage.getByLabel('Username').fill(customer.username);
  await customerPage.getByLabel('Email').fill(`${customer.username}@example.com`);
  await customerPage.getByLabel('Password', { exact: true }).fill(customer.password);
  await customerPage.getByLabel('Confirm password').fill(customer.password);
  await customerPage.getByRole('button', { name: 'Create account' }).click();

  await expect(customerPage.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
  await customerPage.getByLabel('Full name').fill(`E2E Customer ${run}`);
  await customerPage.getByRole('button', { name: 'Save profile' }).click();
  await expect(customerPage.getByRole('heading', { name: `Welcome, ${customer.username}` })).toBeVisible();

  await customerPage.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'My vehicles' }).click();
  await customerPage.getByRole('button', { name: 'Add vehicle' }).first().click();
  await customerPage.getByLabel('VIN').fill(vin);
  await customerPage.getByLabel('Registration').fill(registration);
  await customerPage.getByLabel('Make').fill('Toyota');
  await customerPage.getByLabel('Model').fill('Corolla');
  await customerPage.getByLabel('Year').fill('2021');
  await customerPage.getByLabel('Mileage (miles)').fill('12000');
  await customerPage.getByRole('button', { name: 'Add vehicle' }).click();
  await expect(customerPage.getByText(registration)).toBeVisible();

  await customerPage.goto('/appointments/new');
  await customerPage.getByLabel('Vehicle').selectOption({ label: `${registration} — 2021 Toyota Corolla` });
  await customerPage.getByLabel('Date and time').fill(slot.dateTime);
  await customerPage.getByLabel('Service').selectOption('BRAKES');
  await customerPage.getByLabel('Notes for the workshop').fill('Squeal when braking');
  await customerPage.getByRole('button', { name: 'Book appointment' }).click();
  await expect(customerPage).toHaveURL(/\/appointments$/);

  // ---- Advisor: confirm, mark arrived, check in -------------------------------------------
  const advisor = await signIn(browser, 'advisor1');
  await advisor.goto('/appointments');
  await advisor.getByLabel('Day').fill(slot.date);
  const row = advisor.getByRole('row', { name: new RegExp(registration) });
  await row.getByRole('button', { name: 'Confirm' }).click();
  await row.getByRole('button', { name: 'Mark arrived' }).click();
  await row.getByRole('button', { name: 'Check in' }).click();
  const dialog = advisor.getByRole('dialog', { name: 'Check in vehicle' });
  await dialog.getByLabel('Odometer reading (miles)').fill('12480');
  await dialog.getByRole('button', { name: 'Check in' }).click();

  // Check-in opened the job card in workshop-service.
  await row.getByRole('link', { name: 'Job card' }).click();
  await expect(advisor.getByRole('heading', { name: new RegExp(`Job card · ${registration}`) })).toBeVisible();
  const jobCardUrl = advisor.url();

  // ---- Advisor: inspect and recommend work ------------------------------------------------
  const finding = advisor.getByRole('form', { name: 'Add finding' });
  await finding.getByLabel('Finding').fill('Front pads at 2 mm');
  await finding.getByLabel('Severity').selectOption('HIGH');
  await finding.getByRole('button', { name: 'Add finding' }).click();
  await expect(advisor.getByText('Front pads at 2 mm')).toBeVisible();

  const recommend = advisor.getByRole('form', { name: 'Add recommendation' });
  await recommend.getByLabel('Recommended work').fill(recommendation);
  await recommend.getByLabel('Parts ($)').fill('90.50');
  await recommend.getByLabel('Labour (hours)').fill('1.25');
  await recommend.getByRole('button', { name: 'Add' }).click();
  await expect(advisor.getByText(recommendation)).toBeVisible();
  await advisor.getByRole('button', { name: 'Send to customer for approval' }).click();
  await expect(advisor.getByText('Awaiting approval')).toBeVisible();

  // ---- Customer: approve ----------------------------------------------------------------------
  await customerPage.goto(jobCardUrl);
  await customerPage.getByRole('button', { name: `Approve ${recommendation}` }).click();
  await expect(customerPage.getByText(`by ${customer.username}`)).toBeVisible();

  // ---- Mechanic: start, log work, complete ------------------------------------------------
  const mechanic = await signIn(browser, 'mechanic1');
  await mechanic.goto(jobCardUrl);
  await mechanic.getByRole('button', { name: 'Start work' }).click();
  const work = mechanic.getByRole('form', { name: 'Log work' });
  await work.getByLabel('Work done').fill('Fitted new pads and bedded them in');
  await work.getByRole('button', { name: 'Log work' }).click();
  await expect(mechanic.getByText('Fitted new pads and bedded them in')).toBeVisible();
  await mechanic.getByRole('button', { name: 'Complete job' }).click();
  await expect(mechanic.getByText('Completed', { exact: true }).first()).toBeVisible();

  // ---- Customer: the job is in the vehicle's service history ------------------------------
  await customerPage.goto('/vehicles');
  await customerPage.getByRole('link', { name: new RegExp('Toyota Corolla') }).first().click();
  await expect(customerPage.getByRole('heading', { name: 'Service history' })).toBeVisible();
  await expect(customerPage.getByText(recommendation)).toBeVisible();
  await expect(customerPage.getByText('Fitted new pads and bedded them in', { exact: false })).toBeVisible();
});
