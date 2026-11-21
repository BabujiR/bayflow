import { checkInViaApi, inspectViaApi } from '../e2e/support/bayflow';

import { ADVISOR, customerWithBooking, MECHANIC, signedIn, weekday, workshopExists } from './support/data';
import { demoPassword, expect, SITE_HOST, SLUG, test, WORKSHOP_URL } from './support/presenter';

/**
 * Role clip: the mechanic's job (2–3 minutes). Booking, check-in, estimate and approval happen
 * behind the scenes; the mechanic starts the approved work, logs it and completes the job.
 */
test('Ramplight for mechanics', async ({ presenter: p }) => {
  test.setTimeout(25 * 60_000);
  const password = demoPassword();
  expect(await workshopExists(), `${SLUG}.${SITE_HOST} exists (record 01-walkthrough first)`).toBeTruthy();
  const page = p.page;
  const oil = 'Oil and filter change';
  const airFilter = 'Replace engine air filter';

  const prepared = await customerWithBooking(
    { first: 'Tom', last: 'Becker', car: { make: 'Ford', model: 'F-150', year: 2016 } },
    weekday(3, 14, 30),
    'OIL_CHANGE',
    'Oil change due.',
  );
  const reg = prepared.vehicle.registration;
  const advisorApi = await signedIn(ADVISOR.username, password);
  const jobCardId = await checkInViaApi(advisorApi, prepared.appointmentId, prepared.vehicle.mileage + 20);
  const recommendations = await inspectViaApi(advisorApi, jobCardId, [
    { description: oil, parts: 68, hours: 0.75 },
    { description: airFilter, parts: 24, hours: 0.25 },
  ]);
  for (const recommendation of recommendations) {
    await prepared.customer.api.post(
      `/api/v1/job-cards/${jobCardId}/recommendations/${recommendation.id}/approve`,
    );
  }

  await p.goto(`${WORKSHOP_URL}/login`);
  await p.card(
    'For mechanics',
    'A clear list of approved jobs, and the work, parts and time logged as you go.',
    'Ramplight · Mechanic',
  );
  await p.signIn(MECHANIC.username, password);

  await p.chapter('Your jobs, ready to go', 'Only work the customer has approved.', 5000);
  await p.say('Mike is told when work is approved. My jobs lists it under "Ready to start".');
  await p.nav('My jobs', '/my-jobs');
  const ready = page.getByRole('region', { name: /Ready to start/ });
  await expect(async () => {
    await expect(ready.getByRole('link', { name: new RegExp(reg) })).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 60_000, intervals: [2_000, 4_000] });
  await p.click(ready.getByRole('link', { name: new RegExp(reg) }));
  await p.say("The job card shows the advisor's findings and the approved work.");
  await p.scrollBy(300);
  await p.click(page.getByRole('button', { name: 'Start work' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('In progress');

  await p.chapter('Log as you go', 'Work done, parts used and time spent, per job.', 5000);
  const work = page.getByRole('form', { name: 'Log work' });
  for (const [item, done] of [
    [oil, 'Drained oil, new filter, 6 qt 5W-30.'],
    [airFilter, 'Fitted new air filter; old one heavily clogged.'],
  ] as const) {
    await p.say(`Logs the work for "${item}".`);
    await p.choose(work.getByLabel('Item'), { label: item });
    await p.type(work.getByLabel('Work done'), done);
    await p.click(work.getByRole('button', { name: 'Log work' }));
    await p.expectToast('Work logged');
  }
  await p.say('Adds the parts he used...');
  const part = page.getByRole('form', { name: 'Add part' });
  await p.choose(part.getByLabel('Item'), { label: oil });
  await p.type(part.getByLabel('Part no.'), 'OF-5W30-6');
  await p.type(part.getByLabel('Part', { exact: true }), 'Oil filter and 6 qt 5W-30');
  await p.fill(part.getByLabel('Qty'), '1');
  await p.fill(part.getByLabel('Unit price ($)'), '64.00');
  await p.click(part.getByRole('button', { name: 'Add part' }));
  await p.expectToast('Part added');
  await p.say('...and his time.');
  const labour = page.getByRole('form', { name: 'Add labour' });
  await p.choose(labour.getByLabel('Item'), { label: oil });
  await p.fill(labour.getByLabel('Hours'), '0.75');
  await p.click(labour.getByRole('button', { name: 'Add labour' }));
  await p.expectToast('Labour added');

  await p.say('Done: complete the job. The customer is told their car is ready.');
  await p.click(page.getByRole('button', { name: 'Complete job' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('Completed');
  await p.scrollBy(400);
  await p.say('Totals come from the parts and hours actually used.');
  await p.card('Ramplight', `No paper job sheets to lose. ${SITE_HOST}`, 'For mechanics', 7000);
});
