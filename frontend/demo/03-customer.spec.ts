import { checkInViaApi, inspectViaApi, randomVin, uid } from '../e2e/support/bayflow';

import { ADVISOR, MECHANIC, signedIn, weekday, WORKSHOP, workshopExists } from './support/data';
import { demoPassword, expect, SITE_HOST, SLUG, test, WORKSHOP_URL } from './support/presenter';

/**
 * Role clip: the customer's side (3–4 minutes). They sign up, add a car, book, approve the
 * estimate and see the history. The shop's part happens behind the scenes, through the API.
 */
test('Ramplight for customers', async ({ presenter: p }) => {
  test.setTimeout(25 * 60_000);
  const password = demoPassword();
  expect(await workshopExists(), `${SLUG}.${SITE_HOST} exists (record 01-walkthrough first)`).toBeTruthy();
  const page = p.page;
  const id = uid().toLowerCase();
  const username = `emma-${id}`;
  const registration = `SUM ${id.slice(-3).toUpperCase()}`;
  const at = weekday(3, 11, 30);
  const tyres = 'Replace two front tyres';
  const alignment = 'Four-wheel alignment';

  await p.goto(`${WORKSHOP_URL}/login`);
  await p.card(
    'For your customers',
    'Book online, approve work from the phone, and keep every visit on record.',
    'Ramplight · Customer',
  );
  await p.chapter('Book online in a minute', "On the shop's own site, any time of day.", 5000);
  await p.say("Emma opens her shop's booking page and creates an account.");
  await p.click(page.getByRole('link', { name: 'Create an account' }));
  await p.type(page.getByLabel('Username'), username);
  await p.type(page.getByLabel('Email'), `${username}@example.com`);
  await p.fill(page.getByLabel('Password', { exact: true }), password);
  await p.fill(page.getByLabel('Confirm password'), password);
  await p.click(page.getByRole('button', { name: 'Create account' }));
  await expect(page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
  await p.type(page.getByLabel('Full name'), 'Emma Lopez');
  await p.click(page.getByRole('button', { name: 'Save profile' }));
  await expect(page.getByRole('heading', { name: `Welcome, ${username}` })).toBeVisible();

  await p.say('She adds her car.');
  await p.nav('My vehicles', '/vehicles');
  await p.click(page.getByRole('button', { name: 'Add vehicle' }).first());
  await p.fill(page.getByLabel('VIN'), randomVin());
  await p.type(page.getByLabel('Registration'), registration);
  await p.type(page.getByLabel('Make'), 'Toyota');
  await p.type(page.getByLabel('Model'), 'RAV4');
  await p.fill(page.getByLabel('Year'), '2021');
  await p.fill(page.getByLabel('Mileage (miles)'), '22900');
  await p.click(page.getByRole('button', { name: 'Add vehicle' }));
  await expect(page.getByText(registration).first()).toBeVisible();

  await p.say('And books a tyre check at a time that suits her.');
  await p.nav('Appointments', '/appointments');
  await p.click(page.getByRole('link', { name: 'Book a service' }).first());
  const locationField = page.getByLabel('Location');
  if (await locationField.isVisible().catch(() => false)) {
    await p.choose(locationField, { label: WORKSHOP.mainLocation });
  }
  await p.choose(page.getByLabel('Vehicle'), { label: `${registration} — 2021 Toyota RAV4` });
  await p.fill(page.getByLabel('Date and time'), at.local);
  await p.choose(page.getByLabel('Service'), 'TYRES');
  await p.type(page.getByLabel('Notes for the workshop'), 'Car pulls to the left; front tyres look worn.');
  await p.click(page.getByRole('button', { name: 'Book appointment' }));
  await p.expectToast(/Booked for/);
  await p.say('Booked. She can reschedule or cancel here any time.');

  // Behind the scenes: the shop confirms, checks the car in and sends an estimate.
  const customerApi = await signedIn(username, password);
  const mine = await customerApi.get<{ content: { id: string }[] }>('/api/v1/appointments');
  const appointmentId = mine.content[0]!.id;
  const advisorApi = await signedIn(ADVISOR.username, password);
  const jobCardId = await checkInViaApi(advisorApi, appointmentId, 22_950);
  await inspectViaApi(advisorApi, jobCardId, [
    { description: tyres, parts: 260, hours: 0.75 },
    { description: alignment, parts: 0, hours: 1 },
  ]);

  await p.chapter('Approve work from your phone', 'See every item and its price before any work starts.', 5000);
  await p.say('Emma is told her booking is confirmed, her car is checked in, and her estimate is ready.');
  const estimate = await p.openBellWhenNotified('Your estimate is ready');
  await p.pause(2000);
  await p.click(estimate);
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toBeVisible();
  await p.say('She sees what the shop found and what each job costs, then decides, item by item.');
  await p.scrollBy(400);
  await p.click(page.getByRole('button', { name: `Approve ${tyres}` }));
  await p.expectToast('Decision recorded');
  await p.click(page.getByRole('button', { name: `Approve ${alignment}` }));
  await p.say('Approved, in writing, with her name and the time.');

  // Behind the scenes: the mechanic does the work and the job is completed.
  const mechanicApi = await signedIn(MECHANIC.username, password);
  const card = await advisorApi.get<{ recommendations: { id: string }[] }>(`/api/v1/job-cards/${jobCardId}`);
  await mechanicApi.post(`/api/v1/job-cards/${jobCardId}/start`);
  for (const recommendation of card.recommendations) {
    await mechanicApi.post(`/api/v1/job-cards/${jobCardId}/work-logs`, {
      recommendationId: recommendation.id,
      description: 'Done and checked',
    });
  }
  await advisorApi.post(`/api/v1/job-cards/${jobCardId}/complete`);

  await p.chapter('Ready for pick-up', 'And every visit stays in the car’s history.', 5000);
  const ready = await p.openBellWhenNotified('Your car is ready');
  await p.say('Her car is ready to collect.');
  await p.click(ready);
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toBeVisible();
  await p.scrollBy(400);
  await p.say('The service history keeps every visit, for her and for the shop.');
  await p.nav('My vehicles', '/vehicles');
  await p.click(page.getByRole('link', { name: /Toyota RAV4/ }).first());
  await expect(page.getByRole('heading', { name: 'Service history' })).toBeVisible();
  await p.point(page.getByRole('heading', { name: 'Service history' }));
  await p.pause(2500);
  await p.card('Ramplight', `Online booking for your customers. Start free at ${SITE_HOST}.`, 'For customers', 7000);
});
