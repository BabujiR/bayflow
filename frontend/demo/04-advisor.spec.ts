import { ADVISOR, customerWithBooking, weekday, workshopExists } from './support/data';
import { demoPassword, expect, SITE_HOST, SLUG, test, WORKSHOP_URL } from './support/presenter';

/**
 * Role clip: the service advisor's day (3–4 minutes). A customer's booking is created behind
 * the scenes; the advisor confirms it, checks the car in, inspects it and sends the estimate.
 */
test('Ramplight for service advisors', async ({ presenter: p }) => {
  test.setTimeout(25 * 60_000);
  const password = demoPassword();
  expect(await workshopExists(), `${SLUG}.${SITE_HOST} exists (record 01-walkthrough first)`).toBeTruthy();
  const page = p.page;
  const at = weekday(3, 13);
  const prepared = await customerWithBooking(
    { first: 'Maria', last: 'Gomez', car: { make: 'Toyota', model: 'Camry', year: 2020 } },
    at,
    'BRAKES',
    'Brake warning light came on this morning.',
  );
  const reg = prepared.vehicle.registration;

  await p.goto(`${WORKSHOP_URL}/login`);
  await p.card(
    'For service advisors',
    'Every booking in one place, check-in in seconds, and estimates customers approve online.',
    'Ramplight · Service advisor',
  );
  await p.signIn(ADVISOR.username, password);

  await p.chapter('New bookings come to you', 'No phone tag: customers book online and you are told.', 5000);
  await p.say(`A customer, ${prepared.customer.name}, just booked online. The bell shows it.`);
  await p.openBellWhenNotified(`New booking: ${reg}`);
  await p.pause(2500);
  await page.keyboard.press('Escape');
  await p.say("Today's board: every car for the day, from booked to checked in.");
  await p.nav("Today's board", '/board');
  await p.fill(page.getByLabel('Day'), at.date);
  await expect(page.getByRole('region', { name: 'Booked' })).toContainText(reg);
  await p.point(page.getByRole('region', { name: 'Booked' }));
  await p.pause(1500);

  await p.chapter('Confirm and check in', 'The customer is kept informed at every step.', 5000);
  await p.nav('Appointments', '/appointments');
  await p.fill(page.getByLabel('Day'), at.date);
  const row = page.getByRole('row', { name: new RegExp(reg) }).first();
  await expect(row).toBeVisible();
  await p.say('Confirm the booking. The customer gets a notification.');
  await p.click(row.getByRole('button', { name: 'Confirm' }));
  await p.expectToast('Confirmed');
  await p.say('The car arrives: mark it arrived, then check it in with the mileage.');
  await p.click(row.getByRole('button', { name: 'Mark arrived' }));
  await p.expectToast('Marked as arrived');
  await p.click(row.getByRole('button', { name: 'Check in' }));
  const dialog = page.getByRole('dialog', { name: 'Check in vehicle' });
  await p.fill(dialog.getByLabel('Odometer reading (miles)'), String(prepared.vehicle.mileage + 35));
  await p.click(dialog.getByRole('button', { name: 'Check in' }));
  await p.expectToast(/Checked in/);
  await p.say('A job card opens for the workshop automatically.');

  const jobCardLink = row.getByRole('link', { name: 'Job card' });
  await expect(async () => {
    if (!(await jobCardLink.isVisible())) {
      await page.reload();
      await page.getByLabel('Day').fill(at.date);
    }
    await expect(jobCardLink).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 90_000, intervals: [2_000, 4_000] });
  await p.click(jobCardLink);
  await expect(page.getByRole('heading', { name: new RegExp(`Job card · ${reg}`) })).toBeVisible();

  await p.chapter('Inspect and estimate', 'Findings, recommended work and prices, sent in one click.', 5000);
  const finding = page.getByRole('form', { name: 'Add finding' });
  await p.say('Record what you find, with how urgent it is.');
  await p.type(finding.getByLabel('Finding'), 'Rear pads below minimum; brake fluid dark');
  await p.choose(finding.getByLabel('Severity'), 'HIGH');
  await p.click(finding.getByRole('button', { name: 'Add finding' }));
  await p.say('Recommend the work. Parts plus labour at your rate, priced automatically.');
  const recommend = page.getByRole('form', { name: 'Add recommendation' });
  for (const [description, parts, hours] of [
    ['Replace rear brake pads', '95.00', '1.0'],
    ['Brake fluid flush', '25.00', '0.5'],
  ] as const) {
    await p.type(recommend.getByLabel('Recommended work'), description);
    await p.fill(recommend.getByLabel('Parts ($)'), parts);
    await p.fill(recommend.getByLabel('Labour (hours)'), hours);
    await p.click(recommend.getByRole('button', { name: 'Add' }));
    await expect(page.getByText(description)).toBeVisible();
  }
  await p.say('Send it. The customer approves or declines each item from their phone.');
  await p.click(page.getByRole('button', { name: 'Send to customer for approval' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('Awaiting approval');
  await p.say("You're told the moment they decide, and the mechanics see the approved work.");
  await p.card(
    'Ramplight',
    `Less phone tag, more cars through the bays. ${SITE_HOST}`,
    'For service advisors',
    7000,
  );
});
