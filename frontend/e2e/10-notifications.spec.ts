import type { Actor } from './support/bayflow';
import {
  book,
  checkInViaApi,
  expect,
  inspectViaApi,
  jobCardHeading,
  nextDay,
  seedCustomer,
  slot,
  test,
} from './support/bayflow';

/**
 * Notifications, phase N1: each step of a service tells the people who need to act next, in
 * the app (bell and Notifications page). The other services send events through their outboxes;
 * the bell checks every 30 seconds, so each check reloads the page until the notification is there.
 */
test.describe('Notifications', () => {
  test('a booking, check-in, estimate and finished job each tell the right people', async ({ cast }) => {
    const seeded = await seedCustomer(cast);
    const reg = seeded.vehicle.registration;
    const advisorApi = await cast.api('advisor1');
    const mechanicApi = await cast.api('mechanic1');

    const advisor = await cast.actor('Advisor · advisor1');
    await advisor.signIn('advisor1');
    const customer = await cast.actor('Customer');
    await customer.signIn(seeded.username, seeded.password);

    // ---- The customer books: the location's advisors hear about it -------------------------
    await customer.say('Books a service');
    const appointment = await book(seeded.api, seeded.vehicle.id, slot(nextDay(), 14));
    await expectNotification(advisor, `New booking: ${reg}`);
    await expect(advisor.page.getByTestId('notification-count')).toBeVisible();
    await advisor.check('The bell shows a new booking');

    // ---- Confirmed, checked in: the customer hears both, the mechanics hear the check-in -------
    await advisor.say('Confirms and checks the car in');
    const jobCardId = await checkInViaApi(advisorApi, appointment.id, seeded.vehicle.mileage + 50);
    await expectNotification(customer, 'Booking confirmed');
    await expectNotification(customer, 'Your car is checked in');
    await customer.check('Told the booking is confirmed and the car is checked in');

    const mechanic = await cast.actor('Mechanic · mechanic1');
    await mechanic.signIn('mechanic1');
    await expectNotification(mechanic, `Car checked in: ${reg}`);
    await mechanic.check('Told a car is ready for inspection');

    // ---- The estimate goes to the customer, who opens it from the notification ---------------
    const [brakes] = await inspectViaApi(advisorApi, jobCardId, [
      { description: 'Replace front brake pads', parts: 90, hours: 1 },
    ]);
    const estimate = await expectNotification(customer, 'Your estimate is ready');
    await estimate.click();
    await expect(jobCardHeading(customer)).toBeVisible();
    await customer.check('Clicking the notification opens the job card');

    await customer.say('Approves the work');
    await seeded.api.post(`/api/v1/job-cards/${jobCardId}/recommendations/${brakes.id}/approve`);
    await expectNotification(advisor, `Estimate decided: ${reg}`);
    await expectNotification(mechanic, `Work approved: ${reg}`);
    await advisor.check('The advisor and the mechanics hear the decision');

    // ---- The work is done: the customer can collect the car --------------------------------
    await mechanicApi.post(`/api/v1/job-cards/${jobCardId}/start`);
    await mechanicApi.post(`/api/v1/job-cards/${jobCardId}/work-logs`, {
      recommendationId: brakes.id,
      description: 'Fitted new front pads',
    });
    await advisorApi.post(`/api/v1/job-cards/${jobCardId}/complete`);
    await expectNotification(customer, 'Your car is ready');
    await customer.check('Told the car is ready to collect');

    await customer.say('Marks everything read');
    await customer.page.getByRole('button', { name: 'Mark all read' }).click();
    await expect(customer.page.getByTestId('notification-count')).toHaveCount(0);

    // ---- Nobody else hears about this car -------------------------------------------------
    const stranger = await seedCustomer(cast);
    const other = await cast.actor('Another customer');
    await other.signIn(stranger.username, stranger.password);
    await other.page.goto('/notifications');
    await expect(other.page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
    await expect(other.page.getByText(reg)).toHaveCount(0);
    await other.check("Another customer's notifications say nothing about this car");
  });
});

/**
 * Reloads the Notifications page until one with this title is there (events arrive within
 * seconds; the bell itself would show it within 30). Returns the notification.
 */
async function expectNotification(actor: Actor, title: string) {
  const item = actor.page.getByTestId('notification-item').filter({ hasText: title }).first();
  await expect(async () => {
    await actor.page.goto('/notifications');
    await expect(item).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 60_000, intervals: [1_000, 2_000, 5_000] });
  return item;
}
