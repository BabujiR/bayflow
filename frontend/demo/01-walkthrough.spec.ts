import { randomVin } from '../e2e/support/bayflow';

import {
  ADVISOR,
  BRAKES,
  COVER,
  CUSTOMER,
  LOGO,
  MECHANIC,
  OWNER,
  WIPERS,
  weekday,
  WORKSHOP,
  workshopExists,
} from './support/data';
import {
  demoPassword,
  expect,
  SITE_HOST,
  SITE_URL,
  SLUG,
  test,
  WORKSHOP_URL,
  type Presenter,
} from './support/presenter';

/**
 * The full product walkthrough (10–15 minutes): an auto shop owner signs up and sets up the
 * workshop, a customer books online, the service advisor checks the car in and sends an
 * estimate, the customer approves part of it, the mechanic does the work, the customer
 * collects the car and sees the service history, and the owner looks over the day.
 *
 * Creates the workshop DEMO_SLUG (default summit-auto) on the live site; run it once per slug.
 */
test('Ramplight walkthrough: one auto shop, from sign-up to service history', async ({ presenter: p }) => {
  test.setTimeout(45 * 60_000);
  const password = demoPassword();
  if (await workshopExists()) {
    throw new Error(
      `${SLUG}.${SITE_HOST} already exists. Set DEMO_SLUG to a new address to record the walkthrough again ` +
        '(the role clips 02–05 reuse an existing workshop).',
    );
  }
  const page = p.page;
  const at = weekday(2, 10);
  const car = CUSTOMER.vehicle;
  const carLabel = `${car.registration} — ${car.year} ${car.make} ${car.model}`;

  // ---- Opening ------------------------------------------------------------------------------------
  await page.goto(SITE_URL);
  await p.card(
    'Run your auto shop from booking to service history',
    'One app for the owner, the service advisors, the mechanics and your customers. ' +
      "Let's follow one car through Summit Auto Care.",
    'Ramplight product tour',
    9000,
  );

  // ---- 1. Sign up -----------------------------------------------------------------------------------
  await p.chapter(
    'Set up your shop in two minutes',
    'The owner signs up on getramplight.com. No installation, no IT.',
  );
  await p.say('This is getramplight.com, where any auto shop can start.');
  await p.scrollBy(500);
  await p.say(
    'Online booking, check-in, digital estimates, job cards and service history, all in one place.',
  );
  await p.scrollBy(500);
  await p.pause(1500);
  await p.scrollToTop();
  await p.say('Dana, the owner of Summit Auto Care, clicks "Set up your workshop".');
  await p.click(page.getByRole('link', { name: 'Set up your workshop' }).first());

  const signup = page.getByRole('form', { name: 'Set up your workshop' });
  await expect(signup).toBeVisible();
  await p.say("She enters the shop's name. Ramplight suggests a web address for it.");
  await p.type(signup.getByLabel('Workshop name'), WORKSHOP.name);
  await p.type(signup.getByLabel('Workshop address'), SLUG);
  await expect(signup.getByText(`${SLUG}.${SITE_HOST} is available`)).toBeVisible();
  await p.say(`The shop gets its own address: ${SLUG}.${SITE_HOST}. Customers book there.`);
  await p.choose(signup.getByLabel('Currency'), WORKSHOP.currency);
  await p.choose(signup.getByLabel('Time zone'), WORKSHOP.timeZone);
  await p.say('Currency and time zone, then her own admin account.');
  await p.type(signup.getByLabel('Username'), OWNER.username);
  await p.type(signup.getByLabel('Email'), OWNER.email);
  await p.fill(signup.getByLabel('Password', { exact: true }), password);
  await p.fill(signup.getByLabel('Confirm password'), password);
  await p.click(signup.getByRole('button', { name: 'Create workshop' }));

  await expect(page).toHaveURL(new RegExp(`^${WORKSHOP_URL}/login`));
  await expect(page.getByText('Your workshop is ready')).toBeVisible();
  await p.say('Done. The shop is live at its own address, ready to sign in.');

  // ---- 2. Make it yours -------------------------------------------------------------------------
  await p.chapter(
    'Make it yours',
    'Labour rate, logo, colours, announcements, a second location and the team.',
  );
  await p.signIn(OWNER.username, password);
  await p.say('Dana signs in as the owner. First stop: Workshop settings.');
  await p.nav('Workshop settings', '/settings');

  const settings = page.getByRole('form', { name: 'Workshop settings' });
  await p.say('She sets the hourly labour rate. Every estimate and invoice uses it.');
  await p.fill(settings.getByLabel('Labour rate per hour'), WORKSHOP.labourRate);
  await p.click(settings.getByRole('button', { name: 'Save settings' }));
  await p.expectToast('Workshop settings saved');

  await p.say('Branding: the shop logo replaces ours everywhere its customers look.');
  const logo = page.getByTestId('branding-logo');
  await p.upload(logo.getByLabel('Upload logo'), LOGO, logo);
  await p.expectToast('Logo saved');
  await p.say('A cover photo for the sign-in page.');
  const cover = page.getByTestId('branding-cover');
  await p.upload(cover.getByLabel('Upload sign-in cover photo'), COVER, cover);
  await p.expectToast('Sign-in cover photo saved');

  await p.say('Announcements show at the top of every page: holiday hours, seasonal offers.');
  for (const [text, style] of [
    [WORKSHOP.winterOffer, 'INFO'],
    [WORKSHOP.holidayNotice, 'IMPORTANT'],
  ] as const) {
    await p.click(page.getByRole('button', { name: 'Add announcement' }));
    const dialog = page.getByRole('dialog', { name: 'Add announcement' });
    await p.type(dialog.getByLabel('Message'), text);
    await p.choose(dialog.getByLabel('Style'), style);
    await p.click(dialog.getByRole('button', { name: 'Add announcement' }));
    await p.expectToast('Announcement added');
  }
  await p.say('The important one comes first; the others are one click away.');

  const branding = page.getByRole('form', { name: 'Branding' });
  await p.say("And the shop's own colour for buttons and highlights.");
  await p.tick(branding.getByLabel("Use my workshop's colour"));
  await p.fill(branding.getByLabel('Colour code'), WORKSHOP.colour);
  await p.click(branding.getByRole('button', { name: 'Save branding' }));
  await p.expectToast('Branding saved');
  await p.scrollToTop();
  await p.say('The whole app now wears Summit Auto Care colours.');

  await p.say('Summit has a second site across town. Locations, Add location.');
  await p.nav('Locations', '/locations');
  await p.click(page.getByRole('button', { name: 'Add location' }));
  const location = page.getByRole('form', { name: 'Add location' });
  await p.type(location.getByLabel('Name'), WORKSHOP.second.name);
  await p.type(location.getByLabel('Code'), WORKSHOP.second.code);
  await p.choose(location.getByLabel('Time zone'), WORKSHOP.timeZone);
  await p.fill(location.getByLabel('Labour rate per hour'), WORKSHOP.second.labourRate);
  await p.click(location.getByRole('button', { name: 'Add location' }));
  await p.expectToast(`${WORKSHOP.second.name} saved`);
  await p.say('Each location has its own board, staff, time zone and labour rate.');

  await p.say('Now the team. User accounts, New staff account.');
  await p.nav('User accounts', '/users');
  await addStaff(p, ADVISOR.username, ADVISOR.email, password, 'SERVICE_ADVISOR', [
    WORKSHOP.mainLocation,
    WORKSHOP.second.name,
  ]);
  await p.say(`${ADVISOR.name} is a service advisor at both locations.`);
  await addStaff(p, MECHANIC.username, MECHANIC.email, password, 'MECHANIC', [WORKSHOP.mainLocation]);
  await p.say(`${MECHANIC.name} is a mechanic at Main. Each person only sees what their role needs.`);
  await p.signOut();

  // ---- 3. The customer books online -----------------------------------------------------------------
  await p.chapter('Your customers book online', "On the shop's own branded site, any time of day.");
  await p.say(
    "This is what John, a customer, sees at Summit's address: their logo, photo and announcements.",
  );
  const banner = page.getByTestId('workshop-banner');
  const more = banner.getByRole('button', { name: /more/ });
  if (await more.isVisible().catch(() => false)) {
    await p.click(more);
    await p.say('The seasonal offer is right there.');
  }
  await p.say('John is new here, so he creates an account.');
  await p.click(page.getByRole('link', { name: 'Create an account' }));
  await p.type(page.getByLabel('Username'), CUSTOMER.username);
  await p.type(page.getByLabel('Email'), CUSTOMER.email);
  await p.fill(page.getByLabel('Password', { exact: true }), password);
  await p.fill(page.getByLabel('Confirm password'), password);
  await p.click(page.getByRole('button', { name: 'Create account' }));
  await expect(page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
  await p.type(page.getByLabel('Full name'), CUSTOMER.name);
  await p.click(page.getByRole('button', { name: 'Save profile' }));
  await expect(page.getByRole('heading', { name: `Welcome, ${CUSTOMER.username}` })).toBeVisible();

  await p.say('He adds his car once; every visit is kept against it.');
  await p.nav('My vehicles', '/vehicles');
  await p.click(page.getByRole('button', { name: 'Add vehicle' }).first());
  await p.fill(page.getByLabel('VIN'), randomVin());
  await p.type(page.getByLabel('Registration'), car.registration);
  await p.type(page.getByLabel('Make'), car.make);
  await p.type(page.getByLabel('Model'), car.model);
  await p.fill(page.getByLabel('Year'), car.year);
  await p.fill(page.getByLabel('Mileage (miles)'), car.mileage);
  await p.click(page.getByRole('button', { name: 'Add vehicle' }));
  await expect(page.getByText(car.registration).first()).toBeVisible();

  await p.say('His brakes are grinding. He books a brake check.');
  await p.nav('Appointments', '/appointments');
  await p.click(page.getByRole('link', { name: 'Book a service' }).first());
  await expect(page.getByRole('heading', { name: 'Book a service' })).toBeVisible();
  await p.choose(page.getByLabel('Location'), { label: WORKSHOP.mainLocation });
  await p.choose(page.getByLabel('Vehicle'), { label: carLabel });
  await p.fill(page.getByLabel('Date and time'), at.local);
  await p.choose(page.getByLabel('Service'), 'BRAKES');
  await p.type(page.getByLabel('Notes for the workshop'), 'Grinding noise from the front when braking.');
  await p.click(page.getByRole('button', { name: 'Book appointment' }));
  await p.expectToast(/Booked for/);
  await p.say('Booked. No phone calls, no voicemail. The shop is told right away.');
  await p.signOut();

  // ---- 4. The advisor checks the car in ----------------------------------------------------------
  await p.chapter(
    'The service advisor takes over',
    'New booking, check-in, inspection and a digital estimate.',
  );
  await p.signIn(ADVISOR.username, password);
  await p.say(`${ADVISOR.name} signs in. The bell already shows John's new booking.`);
  await p.openBellWhenNotified(`New booking: ${car.registration}`);
  await p.pause(2500);
  await page.keyboard.press('Escape');

  await p.say("Today's board shows every car for the day, by status.");
  await p.nav("Today's board", '/board');
  await p.fill(page.getByLabel('Day'), at.date);
  await expect(page.getByRole('region', { name: 'Booked' })).toContainText(car.registration);
  await p.point(page.getByRole('region', { name: 'Booked' }));
  await p.say("John's Civic is booked for 10:00.");

  await p.say('In Appointments she confirms the booking. John gets a notification.');
  await p.nav('Appointments', '/appointments');
  await p.fill(page.getByLabel('Day'), at.date);
  const row = page.getByRole('row', { name: new RegExp(car.registration) }).first();
  await expect(row).toBeVisible();
  await p.click(row.getByRole('button', { name: 'Confirm' }));
  await p.expectToast('Confirmed');
  await p.say('On the day, John drops the car off. She marks it arrived...');
  await p.click(row.getByRole('button', { name: 'Mark arrived' }));
  await p.expectToast('Marked as arrived');
  await p.say('...and checks it in with the odometer reading.');
  await p.click(row.getByRole('button', { name: 'Check in' }));
  const checkIn = page.getByRole('dialog', { name: 'Check in vehicle' });
  await p.fill(checkIn.getByLabel('Odometer reading (miles)'), '48310');
  await p.click(checkIn.getByRole('button', { name: 'Check in' }));
  await p.expectToast(/Checked in/);
  await p.say('Check-in opens a job card for the workshop automatically.');

  const jobCardLink = row.getByRole('link', { name: 'Job card' });
  await expect(async () => {
    if (!(await jobCardLink.isVisible())) {
      await page.reload();
      await page.getByLabel('Day').fill(at.date);
    }
    await expect(jobCardLink).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 90_000, intervals: [2_000, 4_000] });
  await p.click(jobCardLink);
  await expect(
    page.getByRole('heading', { name: new RegExp(`Job card · ${car.registration}`) }),
  ).toBeVisible();

  await p.say('She inspects the car and records what she finds.');
  const finding = page.getByRole('form', { name: 'Add finding' });
  await p.type(finding.getByLabel('Finding'), 'Front pads worn to 2 mm; rotors scored');
  await p.choose(finding.getByLabel('Severity'), 'HIGH');
  await p.click(finding.getByRole('button', { name: 'Add finding' }));
  await expect(page.getByText('Front pads worn to 2 mm; rotors scored')).toBeVisible();

  await p.say('Then she recommends the work, with parts and labour. Ramplight prices it.');
  const recommend = page.getByRole('form', { name: 'Add recommendation' });
  for (const item of [BRAKES, WIPERS]) {
    await p.type(recommend.getByLabel('Recommended work'), item.description);
    await p.fill(recommend.getByLabel('Parts ($)'), item.parts);
    await p.fill(recommend.getByLabel('Labour (hours)'), item.hours);
    await p.click(recommend.getByRole('button', { name: 'Add' }));
    await expect(page.getByText(item.description)).toBeVisible();
  }
  await p.say('One click sends the estimate to John for approval. No calls to chase.');
  await p.click(page.getByRole('button', { name: 'Send to customer for approval' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('Awaiting approval');
  await p.signOut();

  // ---- 5. The customer approves ----------------------------------------------------------------------
  await p.chapter(
    'The customer approves on their phone',
    'Item by item, with the price of each, recorded in writing.',
  );
  await p.signIn(CUSTOMER.username, password);
  await p.say('John gets a notification: his estimate is ready.');
  const estimate = await p.openBellWhenNotified('Your estimate is ready');
  await p.pause(1500);
  await p.click(estimate);
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toBeVisible();
  await p.say('He sees what the advisor found and what each job costs.');
  await p.scrollBy(400);
  await p.say('He approves the brakes and declines the wipers for now.');
  await p.click(page.getByRole('button', { name: `Approve ${BRAKES.description}` }));
  await p.expectToast('Decision recorded');
  await p.click(page.getByRole('button', { name: `Decline ${WIPERS.description}` }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('Partially approved');
  await p.say('Every decision is recorded with who made it and when. No disputes at pick-up.');
  await p.signOut();

  // ---- 6. The mechanic does the work -------------------------------------------------------------
  await p.chapter('The mechanic gets to work', 'Only approved work, with parts and hours logged as they go.');
  await p.signIn(MECHANIC.username, password);
  await p.say(`${MECHANIC.name} sees the approved job under "Ready to start".`);
  await p.nav('My jobs', '/my-jobs');
  const ready = page.getByRole('region', { name: /Ready to start/ });
  await p.click(ready.getByRole('link', { name: new RegExp(car.registration) }));
  await p.click(page.getByRole('button', { name: 'Start work' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('In progress');
  await p.say('Only the approved brake job can be worked on. The declined wipers are not offered.');

  const work = page.getByRole('form', { name: 'Log work' });
  await p.type(work.getByLabel('Work done'), 'Fitted new front pads, resurfaced both rotors, road tested.');
  await p.click(work.getByRole('button', { name: 'Log work' }));
  await p.expectToast('Work logged');
  await p.say('He adds the parts he used...');
  const part = page.getByRole('form', { name: 'Add part' });
  await p.type(part.getByLabel('Part no.'), 'BP-4471');
  await p.type(part.getByLabel('Part', { exact: true }), 'Front brake pad set');
  await p.fill(part.getByLabel('Qty'), '1');
  await p.fill(part.getByLabel('Unit price ($)'), '89.00');
  await p.click(part.getByRole('button', { name: 'Add part' }));
  await p.expectToast('Part added');
  await p.say('...and his labour time.');
  const labour = page.getByRole('form', { name: 'Add labour' });
  await p.fill(labour.getByLabel('Hours'), '1.5');
  await p.click(labour.getByRole('button', { name: 'Add labour' }));
  await p.expectToast('Labour added');
  await p.say('Job done. Totals come from the parts and time actually used.');
  await p.click(page.getByRole('button', { name: 'Complete job' }));
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toContainText('Completed');
  await p.scrollBy(500);
  await p.pause(2500);
  await p.signOut();

  // ---- 7. Pick-up and history ---------------------------------------------------------------------
  await p.chapter('Ready for pick-up', 'The customer is told, and the visit goes into the car’s history.');
  await p.signIn(CUSTOMER.username, password);
  await p.say('John is told his car is ready.');
  const readyNote = await p.openBellWhenNotified('Your car is ready');
  await p.click(readyNote);
  await expect(page.getByRole('heading', { name: /Job card ·/ })).toBeVisible();
  await p.say('He can see exactly what was done and what it cost.');
  await p.scrollBy(500);
  await p.pause(2000);
  await p.say("And the car's service history keeps every visit, including what he declined.");
  await p.nav('My vehicles', '/vehicles');
  await p.click(page.getByRole('link', { name: new RegExp(`${car.make} ${car.model}`) }).first());
  await expect(page.getByRole('heading', { name: 'Service history' })).toBeVisible();
  await p.point(page.getByRole('heading', { name: 'Service history' }));
  await p.scrollBy(400);
  await p.say('Next time, the advisor can remind him about those wipers.');
  await p.signOut();

  // ---- 8. The owner's view ----------------------------------------------------------------------------
  await p.chapter("The owner's view", 'Every location, every booking and every job, at a glance.');
  await p.signIn(OWNER.username, password);
  await p.say('Back to Dana. Appointments shows one location or all of them.');
  await p.nav('Appointments', '/appointments');
  const switcher = page.getByRole('combobox', { name: 'Location' });
  if (await switcher.isVisible().catch(() => false)) {
    await p.choose(switcher, { label: 'All locations' });
  }
  await p.fill(page.getByLabel('Day'), at.date);
  await p.point(page.getByRole('row', { name: new RegExp(car.registration) }).first());
  await p.say("John's visit, completed, with its location.");
  await p.say('Job cards: every job, its status and its total.');
  await p.nav('Job cards', '/job-cards');
  await p.pause(2500);
  await p.say('Customers: everyone who has booked, with their cars and history.');
  await p.nav('Customers', '/customers');
  await p.pause(2500);

  await p.card(
    'Ramplight',
    `Online booking · check-in · digital estimates · job cards · service history · your brand, ` +
      `at your own address. Start free at ${SITE_HOST}.`,
    'Thanks for watching',
    10000,
  );
});

async function addStaff(
  p: Presenter,
  username: string,
  email: string,
  password: string,
  role: 'SERVICE_ADVISOR' | 'MECHANIC',
  locations: string[],
): Promise<void> {
  const page = p.page;
  await p.click(page.getByRole('button', { name: 'New staff account' }));
  const dialog = page.getByRole('dialog', { name: 'New staff account' });
  await p.type(dialog.getByLabel('Username'), username);
  await p.type(dialog.getByLabel('Email'), email);
  await p.fill(dialog.getByLabel('Temporary password'), password);
  await p.choose(dialog.getByLabel('Role'), role);
  const worksAt = dialog.getByRole('group', { name: 'Works at' });
  if (await worksAt.isVisible().catch(() => false)) {
    for (const name of locations) {
      await p.tick(worksAt.getByRole('checkbox', { name: new RegExp(`^${name}`) }));
    }
  }
  await p.click(dialog.getByRole('button', { name: 'Create account' }));
  await p.expectToast(`Account ${username} created`);
}
