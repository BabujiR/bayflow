import { ADVISOR, OWNER, WORKSHOP, workshopExists } from './support/data';
import { demoPassword, expect, SITE_HOST, SLUG, test, WORKSHOP_URL } from './support/presenter';

/** Role clip: the owner's tour of the shop's settings and overview (2–3 minutes). Changes nothing. */
test('Ramplight for owners', async ({ presenter: p }) => {
  test.setTimeout(20 * 60_000);
  const password = demoPassword();
  expect(await workshopExists(), `${SLUG}.${SITE_HOST} exists (record 01-walkthrough first)`).toBeTruthy();
  const page = p.page;

  await p.goto(`${WORKSHOP_URL}/login`);
  await p.card(
    'For the shop owner',
    'Set up the shop, the brand, the locations and the team, and see everything at a glance.',
    'Ramplight · Owner',
  );
  await p.chapter('Your shop, your settings', `${WORKSHOP.name} at ${SLUG}.${SITE_HOST}`, 5000);
  await p.say('Your customers sign in on your own branded page: your logo, your photo, your announcements.');
  await p.signIn(OWNER.username, password);

  await p.nav('Workshop settings', '/settings');
  await p.say('Workshop settings: name, currency, time zone and the labour rate used on every estimate.');
  await p.point(page.getByRole('form', { name: 'Workshop settings' }).getByLabel('Labour rate per hour'));
  await p.scrollBy(500);
  await p.say('Branding: your logo and sign-in photo replace ours. PNG or JPEG, checked by the server.');
  await p.point(page.getByTestId('branding-logo'));
  await p.scrollBy(400);
  await p.say('Announcements for holiday hours or seasonal offers. Important ones show first.');
  await p.point(page.getByTestId('announcement-row').first());
  await p.scrollBy(400);
  await p.say('Your colour across the app, and you choose whether "Powered by Ramplight" shows.');
  await p.point(page.getByRole('form', { name: 'Branding' }));
  await p.scrollToTop();

  await p.chapter('Locations and team', 'One account for every site you run.', 5000);
  await p.nav('Locations', '/locations');
  await p.say('Each location has its own time zone, labour rate, board and staff.');
  await p.point(page.getByRole('row', { name: new RegExp(WORKSHOP.second.name) }));
  await p.nav('User accounts', '/users');
  await p.say('Your team: advisors, mechanics and managers, each at the locations they work at.');
  await p.point(page.getByRole('row', { name: new RegExp(ADVISOR.username) }).first());
  await p.say('Someone leaves? Disable the account and they are signed out everywhere, at once.');

  await p.chapter('Everything at a glance', 'Bookings, job cards and customers across every location.', 5000);
  await p.nav('Appointments', '/appointments');
  const switcher = page.getByRole('combobox', { name: 'Location' });
  if (await switcher.isVisible().catch(() => false)) {
    await p.say('Appointments for one location, or all of them.');
    await p.choose(switcher, { label: 'All locations' });
  }
  await p.pause(2000);
  await p.nav('Job cards', '/job-cards');
  await p.say('Every job card: its status, who approved what, and the total.');
  await p.pause(2000);
  await p.nav('Customers', '/customers');
  await p.say('Every customer, their cars and their full service history.');
  await p.pause(2000);
  await p.card('Ramplight', `Start free at ${SITE_HOST}.`, 'For shop owners', 7000);
});
